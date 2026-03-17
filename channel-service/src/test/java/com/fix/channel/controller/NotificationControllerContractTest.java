package com.fix.channel.controller;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.channel.service.ChannelScaffoldService;
import com.fix.channel.vo.NotificationItemVo;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.fix.channel.exception.GlobalExceptionHandler.class)
class NotificationControllerContractTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ChannelScaffoldService channelScaffoldService;

  @Test
  void shouldExposeSseHeartbeatStreamWhenAuthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/notifications/stream")
            .sessionAttr("AUTH_MEMBER_ID", 301L))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(content().string(containsString("event:heartbeat")));
  }

  @Test
  void shouldRequireAuthenticationForNotificationReadEndpoint() throws Exception {
    mockMvc.perform(patch("/api/v1/notifications/1/read"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/notifications/1/read"));
  }

  @Test
  void shouldReturnForbiddenWhenNotificationOwnershipMismatch() throws Exception {
    when(channelScaffoldService.markNotificationRead(301L, 999L))
        .thenThrow(new BusinessException(ErrorCode.CHANNEL_OWNERSHIP_MISMATCH, "access denied"));

    mockMvc.perform(patch("/api/v1/notifications/999/read")
            .sessionAttr("AUTH_MEMBER_ID", 301L))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CHANNEL-006"))
        .andExpect(jsonPath("$.message").value("access denied"));
  }

  @Test
  void shouldMarkReadWhenNotificationOwnedBySessionMember() throws Exception {
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
