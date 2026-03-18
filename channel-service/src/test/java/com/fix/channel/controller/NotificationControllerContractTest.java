package com.fix.channel.controller;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fix.channel.service.ChannelScaffoldService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import com.fix.channel.vo.NotificationItemVo;
import com.fix.channel.vo.NotificationStreamResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerContractTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private ChannelScaffoldService channelScaffoldService;

  @Test
  void 알림_스트림_인증요청시_하트비트_반환() throws Exception {
    when(channelScaffoldService.openNotificationStream(301L)).thenReturn(new SseEmitter(1_000L));

    mockMvc.perform(get("/api/v1/notifications/stream")
            .sessionAttr("AUTH_MEMBER_ID", 301L))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andExpect(result -> {
          String contentType = result.getResponse().getContentType();
          if (contentType != null) {
            org.assertj.core.api.Assertions.assertThat(contentType)
                .contains(MediaType.TEXT_EVENT_STREAM_VALUE);
          }
        });
  }

        @Test
        void 알림_스트림_미인증요청시_401_반환() throws Exception {
          mockMvc.perform(get("/api/v1/notifications/stream"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTH-003"))
          .andExpect(jsonPath("$.message").value("authentication required"))
          .andExpect(jsonPath("$.path").value("/api/v1/notifications/stream"));
        }

  @Test
  void 알림_목록_인증요청시_커서_제한값_적용과_응답_검증() throws Exception {
    when(channelScaffoldService.streamNotifications(argThat(command ->
        command.getMemberId().equals(301L)
            && command.getLimit() == 2
            && command.getCursorId().equals(999L)
    ))).thenReturn(NotificationStreamResult.of(List.of(
        NotificationItemVo.of(802L, "ORDER", "order created", true, null),
        NotificationItemVo.of(801L, "SECURITY", "device logged in", true, Instant.parse("2026-03-17T00:00:10Z"))
    )));

    MvcResult result = mockMvc.perform(get("/api/v1/notifications")
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .param("limit", "2")
            .param("cursorId", "999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items[0].notificationId").value(802))
        .andExpect(jsonPath("$.data.items[0].channel").value("ORDER"))
        .andExpect(jsonPath("$.data.items[0].message").value("order created"))
        .andExpect(jsonPath("$.data.items[0].read").value(false))
        .andExpect(jsonPath("$.data.items[1].notificationId").value(801))
        .andExpect(jsonPath("$.data.items[1].channel").value("SECURITY"))
        .andExpect(jsonPath("$.data.items[1].message").value("device logged in"))
        .andExpect(jsonPath("$.data.items[1].read").value(true))
        .andExpect(jsonPath("$.data.items[1].readAt").value("2026-03-17T00:00:10Z"))
        .andReturn();

    // NON_NULL serialization may omit readAt when it is null.
    var root = objectMapper.readTree(result.getResponse().getContentAsString());
    var firstItem = root.path("data").path("items").get(0);
    if (firstItem != null) {
      org.assertj.core.api.Assertions.assertThat(firstItem.path("readAt").isMissingNode() || firstItem.path("readAt").isNull())
          .isTrue();
    }
  }

  @Test
  void 알림_목록_미인증요청시_401_반환() throws Exception {
    mockMvc.perform(get("/api/v1/notifications"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications"));
  }

  @Test
  void 알림_읽기_미인증요청시_401_반환() throws Exception {
    mockMvc.perform(patch("/api/v1/notifications/1/read"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/1/read"));
  }

  @Test
  void 알림_목록_세션_회원식별자_타입오류시_401_반환() throws Exception {
    mockMvc.perform(get("/api/v1/notifications")
            .sessionAttr("AUTH_MEMBER_ID", "not-a-number"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications"));
  }

  @Test
  void 알림_소유권_불일치_읽기요청시_403_반환() throws Exception {
    when(channelScaffoldService.markNotificationRead(301L, 999L))
        .thenThrow(new BusinessException(ErrorCode.CHANNEL_OWNERSHIP_MISMATCH, "access denied"));

    mockMvc.perform(patch("/api/v1/notifications/999/read")
            .sessionAttr("AUTH_MEMBER_ID", 301L))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("access denied"));
  }

  @Test
  void 알림_소유자_읽음처리_성공_응답() throws Exception {
    when(channelScaffoldService.markNotificationRead(301L, 777L)).thenReturn(
        NotificationItemVo.of(777L, "ORDER", "mark-read", true, Instant.parse("2026-03-17T00:00:00Z"))
    );

    mockMvc.perform(patch("/api/v1/notifications/777/read")
            .sessionAttr("AUTH_MEMBER_ID", 301L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.notificationId").value(777))
        .andExpect(jsonPath("$.data.read").value(true))
        .andExpect(jsonPath("$.data.readAt").value("2026-03-17T00:00:00Z"));
  }
}
