package com.fix.fepgateway.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.controller.FepGatewayController;
import com.fix.fepgateway.support.FepGatewayStandaloneMvcSupport;
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
    mockMvc = FepGatewayStandaloneMvcSupport.build(
        List.of(new CorrelationIdFilter()),
        new FepGatewayController()
    );
  }

  @Test
  void shouldGenerateCorrelationIdWhenHeaderMissing() throws Exception {
    mockMvc.perform(get("/api/v1/ping"))
        .andExpect(status().isOk())
        .andExpect(header().exists(CommonHeaders.X_CORRELATION_ID));
  }

  @Test
  void shouldPreserveProvidedCorrelationId() throws Exception {
    mockMvc.perform(get("/api/v1/ping").header(CommonHeaders.X_CORRELATION_ID, "corr-gateway-001"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-gateway-001"));
  }
}
