package com.fix.channel.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fix.channel.entity.Notification;
import com.fix.channel.repository.NotificationRepository;
import com.fix.channel.vo.NotificationStreamCommand;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_notification_service;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.session.store-type=none",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class ChannelScaffoldNotificationServiceTest {

  @Autowired
  private ChannelScaffoldService channelScaffoldService;

  @Autowired
  private NotificationRepository notificationRepository;

  @BeforeEach
  void setUp() {
    notificationRepository.deleteAll();
  }

  @Test
  void shouldListNotificationsWithCursorPaginationInIdDescOrder() {
    Notification oldMine = savePending(100L, "mine-old");
    savePending(200L, "other");
    Notification newMine = savePending(100L, "mine-new");

    var firstPage = channelScaffoldService.streamNotifications(NotificationStreamCommand.of(100L, 1, null));
    assertThat(firstPage.getItems()).hasSize(1);
    assertThat(firstPage.getItems().get(0).getNotificationId()).isEqualTo(newMine.getId());

    var secondPage = channelScaffoldService.streamNotifications(
        NotificationStreamCommand.of(100L, 10, newMine.getId())
    );
    assertThat(secondPage.getItems()).hasSize(1);
    assertThat(secondPage.getItems().get(0).getNotificationId()).isEqualTo(oldMine.getId());
  }

  @Test
  void shouldMarkOwnedNotificationAsRead() {
    Notification notification = savePending(300L, "read-target");

    var result = channelScaffoldService.markNotificationRead(300L, notification.getId());

    assertThat(result.getNotificationId()).isEqualTo(notification.getId());
    assertThat(result.isRead()).isTrue();
    assertThat(result.getReadAt()).isNotNull();
    assertThat(notificationRepository.findById(notification.getId()))
        .hasValueSatisfying(saved -> assertThat(saved.isRead()).isTrue());
  }

  @Test
  void shouldRejectMarkReadWhenOwnershipMismatch() {
    Notification ownerNotification = savePending(400L, "owner-only");

    assertThatThrownBy(() -> channelScaffoldService.markNotificationRead(401L, ownerNotification.getId()))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CHANNEL_OWNERSHIP_MISMATCH);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldEmitTypedReplayEventToActiveEmitter() throws Exception {
    CapturingEmitter emitter = new CapturingEmitter();
    ConcurrentMap<Long, Set<SseEmitter>> emitters =
        (ConcurrentMap<Long, Set<SseEmitter>>) ReflectionTestUtils.getField(channelScaffoldService, "notificationEmitters");
    emitters.computeIfAbsent(500L, key -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(emitter);

    Map<String, Object> payload = Map.of(
        "type", "ORDER_FAILED",
        "orderSessionId", "sess-500",
        "clOrdId", "cl-500",
        "symbol", "005930",
        "side", "BUY",
        "failureReason", "MANUAL_REJECT",
        "timestamp", Instant.parse("2026-03-17T00:00:00Z")
    );

    channelScaffoldService.bootstrapTypedNotification(500L, "ORDER", "replay failed", "ORDER_FAILED", payload);

    assertThat(notificationRepository.count()).isEqualTo(1);
    assertThat(emitter.eventPrefix()).contains("id:");
    assertThat(emitter.eventPrefix()).contains("event:ORDER_FAILED");
    assertThat(emitter.eventPrefix()).contains("data:");
    assertThat(emitter.payload()).isEqualTo(payload);
    emitters.clear();
  }

  private Notification savePending(Long memberId, String message) {
    return notificationRepository.save(Notification.pending(memberId, "ORDER", message));
  }

  private static final class CapturingEmitter extends SseEmitter {

    private Set<ResponseBodyEmitter.DataWithMediaType> parts;

    @Override
    public void send(SseEventBuilder builder) {
      this.parts = builder.build();
    }

    String eventPrefix() {
      return parts.stream()
          .map(ResponseBodyEmitter.DataWithMediaType::getData)
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .filter(value -> !value.isBlank())
          .findFirst()
          .orElse("");
    }

    Object payload() {
      return parts.stream()
          .filter(part -> part.getMediaType() == null || MediaType.APPLICATION_JSON.includes(part.getMediaType()))
          .map(ResponseBodyEmitter.DataWithMediaType::getData)
          .filter(data -> !(data instanceof String))
          .findFirst()
          .orElse(null);
    }
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    @Primary
    java.time.Clock notificationTestClock() {
      return java.time.Clock.fixed(Instant.parse("2026-03-17T00:00:00Z"), ZoneOffset.UTC);
    }
  }
}
