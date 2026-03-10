package com.fix.corebank.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.web.CommonHeaders;
import com.fix.corebank.filter.CorrelationIdFilter;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.service.AccountProvisioningService;
import com.fix.corebank.service.CorebankOrderPersistenceService;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.support.CorebankStandaloneMvcSupport;
import com.fix.corebank.vo.AccountProvisioningCommand;
import com.fix.corebank.vo.AccountProvisioningResult;
import com.fix.corebank.vo.InternalOrderResult;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderRequeryCommand;
import com.fix.corebank.vo.PortfolioQueryCommand;
import com.fix.corebank.vo.PortfolioResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class CorebankInternalApiSkeletonTest {

  private static final String CORE_CL_ORD_ID_1 = "123e4567-e89b-42d3-a456-426614174210";
  private static final String CORE_CL_ORD_ID_2 = "123e4567-e89b-42d3-a456-426614174211";
  private static final String CORE_CL_ORD_ID_3 = "123e4567-e89b-42d3-a456-426614174212";

  private MockMvc mockMvc;
  private StubCorebankOrderService corebankOrderService;
  private StubAccountProvisioningService accountProvisioningService;

  @BeforeEach
  void setUp() {
    corebankOrderService = new StubCorebankOrderService();
    accountProvisioningService = new StubAccountProvisioningService();
    mockMvc = CorebankStandaloneMvcSupport.build(
        List.of(
            new CorrelationIdFilter(),
            new com.fix.corebank.security.InternalSecretFilter(
                "test-secret",
                JsonMapper.builder().findAndAddModules().build()
            )
        ),
        new InternalCorebankController(corebankOrderService, accountProvisioningService)
    );
  }

  @Test
  void shouldSupportInternalPortfolioAndOrderEndpoints() throws Exception {
    String correlationId = "66cf95fd-3660-48a6-9e71-7ed42257b748";
    accountProvisioningService.setProvisioningResults(
        AccountProvisioningResult.of(
            1L,
            "11000000000301",
            "ACTIVE",
            false,
            301L,
            Instant.parse("2026-03-01T10:00:00Z")
        ),
        AccountProvisioningResult.of(
            1L,
            "11000000000301",
            "ACTIVE",
            true,
            301L,
            Instant.parse("2026-03-01T10:00:00Z")
        )
    );

    corebankOrderService.setPortfolioResult(PortfolioResult.of(
        1L,
        "ACC-1001",
        "005930",
        new BigDecimal("120.0000"),
        new BigDecimal("500.0000"),
        BigDecimal.ZERO
    ));
    corebankOrderService.setCreateOrderResult(InternalOrderResult.of(
        1001L,
        CORE_CL_ORD_ID_1,
        "FILLED",
        false,
        new BigDecimal("2.0000")
    ));
    corebankOrderService.setRequeryOrderResult(InternalOrderResult.of(
        1001L,
        CORE_CL_ORD_ID_1,
        "FILLED",
        true,
        new BigDecimal("2.0000")
    ));

    mockMvc.perform(post("/internal/v1/portfolio")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, correlationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "memberId": 301,
                  "memberNo": "M-301",
                  "email": "member301@fix.local"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.idempotent").value(false));

    mockMvc.perform(post("/internal/v1/portfolio")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, correlationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "memberId": 301,
                  "memberNo": "M-301",
                  "email": "member301@fix.local"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.idempotent").value(true));

    mockMvc.perform(get("/internal/v1/portfolio")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("symbol", "005930"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.symbol").value("005930"));

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_1)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.status").value("FILLED"));

    mockMvc.perform(get("/internal/v1/orders/{clOrdId}/requery", CORE_CL_ORD_ID_1)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("attemptCount", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.status").value("FILLED"));
  }

  @Test
  void shouldExposeSchedulerRequeryMetadataAndValidateAttemptCount() throws Exception {
    corebankOrderService.setRequeryOrderResult(InternalOrderResult.requery(
        1002L,
        CORE_CL_ORD_ID_2,
        "UNKNOWN",
        true,
        new BigDecimal("2.0000"),
        "still unresolved",
        false,
        true,
        5,
        5
    ));

    mockMvc.perform(get("/internal/v1/orders/{clOrdId}/requery", CORE_CL_ORD_ID_2)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("attemptCount", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_2))
        .andExpect(jsonPath("$.data.status").value("UNKNOWN"))
        .andExpect(jsonPath("$.data.message").value("still unresolved"))
        .andExpect(jsonPath("$.data.retriable").value(false))
        .andExpect(jsonPath("$.data.escalationRequired").value(true))
        .andExpect(jsonPath("$.data.attemptCount").value(5))
        .andExpect(jsonPath("$.data.maxRetryCount").value(5));

    mockMvc.perform(get("/internal/v1/orders/{clOrdId}/requery", CORE_CL_ORD_ID_2)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("attemptCount", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void shouldRejectFractionalOrderInputsBeforeCallingFepClient() throws Exception {
    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_2)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.5000")
            .param("price", "70100.1000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

    org.assertj.core.api.Assertions.assertThat(corebankOrderService.createOrderCalls()).isZero();
  }

  @Test
  void shouldReturn422WhenProvisioningPayloadMissesMemberId() throws Exception {
    accountProvisioningService.setProvisioningFailure(new BusinessException(
        ErrorCode.CONTRACT_VALIDATION_FAILED,
        "memberId is required"
    ));

    mockMvc.perform(post("/internal/v1/portfolio")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "a2d4c77d-7449-44ff-bec8-f2c1cf9f512c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "memberNo": "M-INVALID",
                  "email": "member-invalid@fix.local"
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION-001"));
  }

  @Test
  void shouldExposeUserMessageKeyAndOperatorCodeForMappedExternalErrors() throws Exception {
    corebankOrderService.setCreateOrderFailure(new BusinessException(
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage(),
        new ErrorMetadata("error.fep.timeout", "TIMEOUT")
    ));

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_3)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("FEP-002"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.timeout"))
        .andExpect(jsonPath("$.operatorCode").value("TIMEOUT"));
  }

  private static final class StubCorebankOrderService extends CorebankOrderService {

    private PortfolioResult portfolioResult;
    private InternalOrderResult createOrderResult;
    private InternalOrderResult requeryOrderResult;
    private RuntimeException createOrderFailure;
    private int createOrderCalls;

    private StubCorebankOrderService() {
      super(
          (AccountRepository) null,
          (PositionRepository) null,
          (ExecutionRepository) null,
          (CorebankOrderPersistenceService) null,
          null
      );
    }

    @Override
    public PortfolioResult getPortfolio(PortfolioQueryCommand command) {
      return portfolioResult;
    }

    @Override
    public InternalOrderResult createOrder(InternalOrderCreateCommand command) {
      createOrderCalls++;
      if (createOrderFailure != null) {
        throw createOrderFailure;
      }
      return createOrderResult;
    }

    @Override
    public InternalOrderResult requeryOrder(InternalOrderRequeryCommand command) {
      return requeryOrderResult;
    }

    private void setPortfolioResult(PortfolioResult portfolioResult) {
      this.portfolioResult = portfolioResult;
    }

    private void setCreateOrderResult(InternalOrderResult createOrderResult) {
      this.createOrderResult = createOrderResult;
    }

    private void setRequeryOrderResult(InternalOrderResult requeryOrderResult) {
      this.requeryOrderResult = requeryOrderResult;
    }

    private void setCreateOrderFailure(RuntimeException createOrderFailure) {
      this.createOrderFailure = createOrderFailure;
    }

    private int createOrderCalls() {
      return createOrderCalls;
    }
  }

  private static final class StubAccountProvisioningService extends AccountProvisioningService {

    private final List<AccountProvisioningResult> results = new ArrayList<>();
    private RuntimeException provisioningFailure;
    private int nextIndex;

    private StubAccountProvisioningService() {
      super(null, null, null);
    }

    @Override
    public AccountProvisioningResult provisionDefaultAccount(AccountProvisioningCommand command) {
      if (provisioningFailure != null) {
        throw provisioningFailure;
      }
      if (nextIndex >= results.size()) {
        throw new IllegalStateException("No stub provisioning result configured");
      }
      return results.get(nextIndex++);
    }

    private void setProvisioningResults(AccountProvisioningResult... provisioningResults) {
      results.clear();
      results.addAll(List.of(provisioningResults));
      provisioningFailure = null;
      nextIndex = 0;
    }

    private void setProvisioningFailure(RuntimeException provisioningFailure) {
      results.clear();
      this.provisioningFailure = provisioningFailure;
      nextIndex = 0;
    }
  }
}
