package com.fix.fepsimulator.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "internal.secret=test-secret")
class FepSimulatorInternalSecretFilterTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldBlockInternalRouteWithoutSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldAllowInternalRouteWithSecret() throws Exception {
    mockMvc.perform(get("/fep-internal/v1/ping").header("X-Internal-Secret", "test-secret"))
        .andExpect(status().isOk());
  }
}
