package com.fix.fepsimulator.security;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fix.common.web.CommonHeaders;
import com.fix.fepsimulator.controller.FepSimulatorRuleController;
import com.fix.fepsimulator.filter.CorrelationIdFilter;
import com.fix.fepsimulator.repository.SimulatorRuleRepository;
import com.fix.fepsimulator.service.FepSimulatorControlService;
import com.fix.fepsimulator.support.FepSimulatorStandaloneMvcSupport;
import com.fix.fepsimulator.vo.SimulatorRuleResult;
import com.fix.fepsimulator.vo.SimulatorRuleUpsertCommand;

@ExtendWith(MockitoExtension.class)
class InternalSecretFilterTest {

  private MockMvc mockMvc;
  private StubControlService stubControlService;

  @BeforeEach
  void setUp() {
    stubControlService = new StubControlService();
    mockMvc = FepSimulatorStandaloneMvcSupport.build(
        List.of(
            new CorrelationIdFilter(),
            new InternalSecretFilter("test-secret", JsonMapper.builder().findAndAddModules().build())
        ),
      new FepSimulatorRuleController(stubControlService)
    );
  }

  @Test
  void shouldBlockMutationWithoutSecret() throws Exception {
    mockMvc.perform(put("/fep-internal/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  \"action\": \"TIMEOUT\",
                  \"targetExchange\": \"KRX\",
                  \"ttlSeconds\": 30
                }
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists(CommonHeaders.X_CORRELATION_ID))
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Secret"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());

    org.assertj.core.api.Assertions.assertThat(stubControlService.applyCount()).isZero();
  }

  @Test
  void shouldBlockQueryWithoutSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/rules").header(CommonHeaders.X_CORRELATION_ID, "corr-simulator-unauthorized"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-simulator-unauthorized"))
        .andExpect(jsonPath("$.correlationId").value("corr-simulator-unauthorized"));

    org.assertj.core.api.Assertions.assertThat(stubControlService.listCount()).isZero();
  }

  @Test
  void shouldBlockClearWithoutSecret() throws Exception {
    mockMvc.perform(delete("/fep-internal/rules"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"));

    org.assertj.core.api.Assertions.assertThat(stubControlService.clearCount()).isZero();
  }

  @Test
  void shouldAllowInternalRouteWithSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/rules").header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk());
  }

  private static final class StubControlService extends FepSimulatorControlService {

    private final AtomicInteger applyInvocations = new AtomicInteger();
    private final AtomicInteger listInvocations = new AtomicInteger();
    private final AtomicInteger clearInvocations = new AtomicInteger();

    private StubControlService() {
      super((SimulatorRuleRepository) null);
    }

    @Override
    public SimulatorRuleResult applyRule(SimulatorRuleUpsertCommand command, String requestUri, String requestSource) {
      applyInvocations.incrementAndGet();
      return null;
    }

    @Override
    public List<SimulatorRuleResult> listActiveRules() {
      listInvocations.incrementAndGet();
      return Collections.emptyList();
    }

    @Override
    public int clearRules(String requestUri, String requestSource) {
      clearInvocations.incrementAndGet();
      return 0;
    }

    private int applyCount() {
      return applyInvocations.get();
    }

    private int listCount() {
      return listInvocations.get();
    }

    private int clearCount() {
      return clearInvocations.get();
    }
  }
}
