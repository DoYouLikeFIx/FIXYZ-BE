package com.fix.fepgateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class FepGatewayProdDocsDisabledTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldDisableApiDocsInProdProfile() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
  }
}
