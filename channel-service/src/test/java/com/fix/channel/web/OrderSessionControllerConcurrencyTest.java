package com.fix.channel.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.channel.controller.OrderSessionController;
import com.fix.channel.dto.request.OrderSessionCreateRequest;
import com.fix.channel.dto.response.OrderSessionResponse;
import com.fix.channel.session.ChannelSessionAttributes;
import com.fix.channel.session.ChannelSessionRequestLock;
import com.fix.channel.service.OrderSessionService;
import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.channel.vo.OrderSessionResult;
import com.fix.common.error.ApiResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class OrderSessionControllerConcurrencyTest {

  @Test
  void shouldSerializeConcurrentCreatesBeforeConsumingFreshLoginBypass() throws Exception {
    Instant now = Instant.now();
    CountDownLatch firstCallEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstCall = new CountDownLatch(1);
    AtomicInteger callCount = new AtomicInteger();
    List<Boolean> bypassFlags = new CopyOnWriteArrayList<>();
    OrderSessionService orderSessionService = new OrderSessionService(null, null, null, null, null, null) {
      @Override
      public OrderSessionResult createOrderSession(OrderSessionCreateCommand command) {
        bypassFlags.add(command.isChallengeBypassEligible());

        int currentCall = callCount.incrementAndGet();
        if (currentCall == 1) {
          firstCallEntered.countDown();
          assertThat(awaitQuietly(releaseFirstCall, 2)).isTrue();
          return OrderSessionResult.of(
              "session-auth-1",
              command.getClOrdId(),
              "AUTHED",
              now.plusSeconds(600),
              600L,
              false,
              "LOGIN_MFA_FRESH",
              true
          );
        }

        return OrderSessionResult.of(
            "session-stepup-2",
            command.getClOrdId(),
            "PENDING_NEW",
            now.plusSeconds(600),
            600L,
            true,
            "STEP_UP_REQUIRED",
            true
        );
      }
    };
    OrderSessionController controller = new OrderSessionController(
        orderSessionService,
        new ChannelSessionRequestLock(new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class))
    );
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(ChannelSessionAttributes.AUTH_MEMBER_ID, 301L);
    session.setAttribute(ChannelSessionAttributes.AUTH_MEMBER_NAME, "Concurrent User");
    session.setAttribute(ChannelSessionAttributes.AUTH_MFA_VERIFIED_AT, now);
    session.setAttribute(ChannelSessionAttributes.AUTH_LOGIN_AUTHENTICATED_AT, now.minusSeconds(5));
    session.setAttribute(ChannelSessionAttributes.AUTH_ORDER_CHALLENGE_BYPASS_ELIGIBLE, true);
    session.setAttribute(ChannelSessionAttributes.AUTH_LOGIN_IP_ADDRESS, "127.0.0.1");
    session.setAttribute(ChannelSessionAttributes.AUTH_LOGIN_USER_AGENT, "test-agent");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<ResponseEntity<ApiResponse<OrderSessionResponse>>> firstResponse = executor.submit(
          () -> controller.create(
              new OrderSessionCreateRequest("123e4567-e89b-42d3-a456-426614174280", "ORD-REF-280"),
              buildCreateRequest(session)
          )
      );

      assertThat(firstCallEntered.await(1, TimeUnit.SECONDS)).isTrue();

      Future<ResponseEntity<ApiResponse<OrderSessionResponse>>> secondResponse = executor.submit(
          () -> controller.create(
              new OrderSessionCreateRequest("123e4567-e89b-42d3-a456-426614174281", "ORD-REF-281"),
              buildCreateRequest(session)
          )
      );

      Thread.sleep(200L);
      assertThat(callCount.get()).isEqualTo(1);

      releaseFirstCall.countDown();

      ResponseEntity<ApiResponse<OrderSessionResponse>> first = firstResponse.get(2, TimeUnit.SECONDS);
      ResponseEntity<ApiResponse<OrderSessionResponse>> second = secondResponse.get(2, TimeUnit.SECONDS);

      assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(first.getBody()).isNotNull();
      assertThat(first.getBody().getData()).isNotNull();
      assertThat(first.getBody().getData().status()).isEqualTo("AUTHED");

      assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(second.getBody()).isNotNull();
      assertThat(second.getBody().getData()).isNotNull();
      assertThat(second.getBody().getData().status()).isEqualTo("PENDING_NEW");

      assertThat(bypassFlags).containsExactly(true, false);
      assertThat((Object) session.getAttribute(ChannelSessionAttributes.AUTH_ORDER_CHALLENGE_BYPASS_ELIGIBLE))
          .isEqualTo(false);
    } finally {
      executor.shutdownNow();
    }
  }

  private MockHttpServletRequest buildCreateRequest(MockHttpSession session) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders/sessions");
    request.setSession(session);
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("User-Agent", "test-agent");
    return request;
  }

  private boolean awaitQuietly(CountDownLatch latch, int timeoutSeconds) {
    try {
      return latch.await(timeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for concurrent controller test", ex);
    }
  }
}
