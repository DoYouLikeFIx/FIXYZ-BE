package com.fix.fepsimulator.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fix.common.web.CommonHeaders;
import com.fix.fepsimulator.controller.FepSimulatorController;
import com.fix.fepsimulator.filter.CorrelationIdFilter;
import com.fix.fepsimulator.support.FepSimulatorStandaloneMvcSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class InternalSecretFilterTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = FepSimulatorStandaloneMvcSupport.build(
        List.of(
            new CorrelationIdFilter(),
            new InternalSecretFilter("test-secret", JsonMapper.builder().findAndAddModules().build())
        ),
        new FepSimulatorController()
    );
  }

  @Test
  void shouldBlockInternalRouteWithoutSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists(CommonHeaders.X_CORRELATION_ID))
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Secret"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void shouldPreserveProvidedCorrelationIdForUnauthorizedInternalRoute() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping").header(CommonHeaders.X_CORRELATION_ID, "corr-simulator-unauthorized"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-simulator-unauthorized"))
        .andExpect(jsonPath("$.correlationId").value("corr-simulator-unauthorized"));
  }

  @Test
  void shouldAllowInternalRouteWithSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping").header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk());
  }
}
