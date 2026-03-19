package com.fix.fepsimulator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fix.common.web.CommonHeaders;
import com.fix.fepsimulator.filter.CorrelationIdFilter;
import com.fix.fepsimulator.repository.SimulatorRuleRepository;
import com.fix.fepsimulator.security.InternalSecretFilter;
import com.fix.fepsimulator.service.FepSimulatorControlService;
import com.fix.fepsimulator.support.FepSimulatorStandaloneMvcSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
class FepSimulatorInternalPingLoggingTest {

  private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = FepSimulatorStandaloneMvcSupport.build(
        java.util.List.of(
            new CorrelationIdFilter(),
            new InternalSecretFilter("test-secret", JsonMapper.builder().findAndAddModules().build())
        ),
        new FepSimulatorController(new FepSimulatorControlService((SimulatorRuleRepository) null))
    );
  }

  @Test
  void shouldLogSuccessfulInternalPing(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-simulator-log-001")
            .header(CommonHeaders.TRACEPARENT, TRACEPARENT))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-simulator-log-001"))
        .andExpect(header().string(CommonHeaders.TRACEPARENT, TRACEPARENT));

    assertThat(output.getOut())
        .contains("operation=SIMULATOR_TRACE_DIAGNOSTIC_RECEIVED")
        .contains("correlationId=trace-simulator-log-001")
        .contains("traceparent=" + TRACEPARENT)
        .contains("boundary=open");
  }
}
