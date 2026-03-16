package com.fix.corebank.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.corebank.controller.CorebankController;
import com.fix.corebank.support.CorebankStandaloneMvcSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = CorebankStandaloneMvcSupport.build(
        List.of(new CorrelationIdFilter()),
        new CorebankController()
    );
  }

  @Test
  void shouldGenerateCorrelationIdWhenHeaderMissing() throws Exception {
    mockMvc.perform(get("/api/v1/ping"))
        .andExpect(status().isOk())
        .andExpect(header().exists(CommonHeaders.X_CORRELATION_ID))
        .andExpect(header().exists(CommonHeaders.TRACEPARENT));
  }

  @Test
  void shouldPreserveProvidedCorrelationId() throws Exception {
    mockMvc.perform(get("/api/v1/ping").header(CommonHeaders.X_CORRELATION_ID, "corr-corebank-001"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-corebank-001"));
  }

  @Test
  void shouldPreserveProvidedTraceparent() throws Exception {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    mockMvc.perform(get("/api/v1/ping").header(CommonHeaders.TRACEPARENT, traceparent))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.TRACEPARENT, traceparent));
  }
}
