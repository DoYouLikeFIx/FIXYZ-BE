package com.fix.channel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.channel.dto.response.AdminMonitoringFreshnessResponse;
import com.fix.channel.service.AdminApiRateLimitService;
import com.fix.channel.service.AdminMemberSessionService;
import com.fix.channel.service.AdminMonitoringFreshnessService;
import com.fix.channel.service.AdminOrderIdempotencyReconciliationService;
import com.fix.channel.support.ChannelContainersIntegrationTestBase;
import com.fix.channel.vo.AdminMonitoringFreshnessResult;
import com.fix.channel.vo.AdminOrderIdempotencyReconciliationResult;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.RetryAfterBusinessException;
import com.fix.common.web.CommonHeaders;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerContractTest extends ChannelContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminMemberSessionService adminMemberSessionService;

  @MockitoBean
  private AdminOrderIdempotencyReconciliationService adminOrderIdempotencyReconciliationService;

  @MockitoBean
  private AdminMonitoringFreshnessService adminMonitoringFreshnessService;

  @MockitoBean
  private AdminApiRateLimitService adminApiRateLimitService;

  @Test
  @WithMockUser(username = "admin-contract@fixyz.com", roles = "ADMIN")
  void shouldReturnMonitoringFreshnessForAdminEndpoint() throws Exception {
    doNothing().when(adminApiRateLimitService).enforceMonitoringFreshness(anyString());
    when(adminMonitoringFreshnessService.getFreshness()).thenReturn(
        new AdminMonitoringFreshnessResult(List.of(
            new AdminMonitoringFreshnessResult.Item(
                "executionVolume",
                "live",
                "Prometheus freshness healthy (30s old)",
                Instant.parse("2026-03-24T09:19:30Z")
            ),
            new AdminMonitoringFreshnessResult.Item(
                "pendingSessions",
                "stale",
                "Prometheus freshness stale (5m old)",
                Instant.parse("2026-03-24T09:15:00Z")
            )
        ))
    );

    mockMvc.perform(get("/api/v1/admin/monitoring/freshness")
            .sessionAttr("AUTH_MEMBER_ID", 901L)
            .sessionAttr(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                "admin-contract@fixyz.com"
            )
            .header(CommonHeaders.X_CORRELATION_ID, "trace-admin-monitoring-freshness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items[0].key").value("executionVolume"))
        .andExpect(jsonPath("$.data.items[0].status").value("live"))
        .andExpect(jsonPath("$.data.items[0].statusMessage").value("Prometheus freshness healthy (30s old)"))
        .andExpect(jsonPath("$.data.items[0].lastUpdatedAt").value("2026-03-24T09:19:30Z"))
        .andExpect(jsonPath("$.data.items[1].key").value("pendingSessions"))
        .andExpect(jsonPath("$.data.items[1].status").value("stale"));
  }

  @Test
  @WithMockUser(username = "admin-contract@fixyz.com", roles = "ADMIN")
  void shouldReturnReconciliationSummaryForAdminEndpoint() throws Exception {
    when(adminMemberSessionService.resolveOperatorId(901L)).thenReturn("OPS-admin-contract");
    doNothing().when(adminApiRateLimitService).enforceOrderReconciliation(anyString());
    when(adminOrderIdempotencyReconciliationService.reconcile(eq("CL-ADMIN-1"), any()))
        .thenReturn(AdminOrderIdempotencyReconciliationResult.of(
            "CL-ADMIN-1",
            "OS-9001",
            "RESTORED",
            null,
            "FEP-KRX-CL-ADMIN-1",
            "CONFIRMED",
            "reconciled external linkage from corebank evidence",
            1,
            1,
            0,
            0
        ));

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/idempotency-reconciliation", "CL-ADMIN-1")
            .with(csrf())
            .sessionAttr("AUTH_MEMBER_ID", 901L)
            .sessionAttr(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                "admin-contract@fixyz.com"
            )
            .header(CommonHeaders.X_CORRELATION_ID, "trace-admin-reconcile")
            .header("X-Forwarded-For", "203.0.113.10, 10.0.0.2")
            .header("User-Agent", "JUnit-Admin-Contract/1.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.clOrdId").value("CL-ADMIN-1"))
        .andExpect(jsonPath("$.data.orderSessionId").value("OS-9001"))
        .andExpect(jsonPath("$.data.outcome").value("RESTORED"))
        .andExpect(jsonPath("$.data.mismatchType").doesNotExist())
        .andExpect(jsonPath("$.data.externalOrderId").value("FEP-KRX-CL-ADMIN-1"))
        .andExpect(jsonPath("$.data.externalSyncStatus").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.message").value("reconciled external linkage from corebank evidence"))
        .andExpect(jsonPath("$.data.scanned").value(1))
        .andExpect(jsonPath("$.data.restored").value(1))
        .andExpect(jsonPath("$.data.mismatched").value(0))
        .andExpect(jsonPath("$.data.failed").value(0));
  }

  @Test
  @WithMockUser(username = "admin-contract@fixyz.com", roles = "ADMIN")
  void shouldExposeRetryAfterWhenReconciliationRateLimitIsExceeded() throws Exception {
    when(adminMemberSessionService.resolveOperatorId(901L)).thenReturn("OPS-admin-contract");
    doThrow(new RetryAfterBusinessException(
        ErrorCode.RATE_LIMIT_EXCEEDED,
        "rate limit exceeded",
        17L
    )).when(adminApiRateLimitService).enforceOrderReconciliation(anyString());

    mockMvc.perform(post("/api/v1/admin/orders/{clOrdId}/idempotency-reconciliation", "CL-ADMIN-1")
            .with(csrf())
            .sessionAttr("AUTH_MEMBER_ID", 901L)
            .sessionAttr(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                "admin-contract@fixyz.com"
            )
            .header(CommonHeaders.X_CORRELATION_ID, "trace-admin-reconcile-rate-limit")
            .header("User-Agent", "JUnit-Admin-Contract/1.0"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "17"))
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-admin-reconcile-rate-limit"))
        .andExpect(jsonPath("$.code").value(ErrorCode.RATE_LIMIT_EXCEEDED.code()))
        .andExpect(jsonPath("$.message").value("rate limit exceeded"))
        .andExpect(jsonPath("$.path").value("/api/v1/admin/orders/CL-ADMIN-1/idempotency-reconciliation"));

    verifyNoInteractions(adminOrderIdempotencyReconciliationService);
  }

  @Test
  @WithMockUser(username = "admin-contract@fixyz.com", roles = "ADMIN")
  void shouldExposeRetryAfterWhenMonitoringFreshnessRateLimitIsExceeded() throws Exception {
    doThrow(new RetryAfterBusinessException(
        ErrorCode.RATE_LIMIT_EXCEEDED,
        "rate limit exceeded",
        11L
    )).when(adminApiRateLimitService).enforceMonitoringFreshness(anyString());

    mockMvc.perform(get("/api/v1/admin/monitoring/freshness")
            .sessionAttr("AUTH_MEMBER_ID", 901L)
            .sessionAttr(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                "admin-contract@fixyz.com"
            )
            .header(CommonHeaders.X_CORRELATION_ID, "trace-admin-monitoring-rate-limit"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "11"))
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-admin-monitoring-rate-limit"))
        .andExpect(jsonPath("$.code").value(ErrorCode.RATE_LIMIT_EXCEEDED.code()))
        .andExpect(jsonPath("$.message").value("rate limit exceeded"))
        .andExpect(jsonPath("$.path").value("/api/v1/admin/monitoring/freshness"));

    verifyNoInteractions(adminMonitoringFreshnessService);
  }
}
