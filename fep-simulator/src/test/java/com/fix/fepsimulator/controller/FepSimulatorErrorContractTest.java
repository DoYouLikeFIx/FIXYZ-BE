package com.fix.fepsimulator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FepSimulatorErrorContractTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldReturnStandardizedErrorEnvelope() throws Exception {
    mockMvc.perform(get("/api/v1/errors/boom"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("simulator bad request"))
        .andExpect(jsonPath("$.path").value("/api/v1/errors/boom"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }
}
