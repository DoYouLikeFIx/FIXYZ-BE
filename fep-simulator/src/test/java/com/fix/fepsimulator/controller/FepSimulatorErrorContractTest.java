package com.fix.fepsimulator.controller;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.fepsimulator.filter.CorrelationIdFilter;
import com.fix.fepsimulator.repository.SimulatorRuleRepository;
import com.fix.fepsimulator.service.FepSimulatorControlService;
import com.fix.fepsimulator.support.FepSimulatorStandaloneMvcSupport;

@ExtendWith(MockitoExtension.class)
class FepSimulatorErrorContractTest {

  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    mockMvc = FepSimulatorStandaloneMvcSupport.build(
        List.of(new CorrelationIdFilter()),
      new FepSimulatorController(new FepSimulatorControlService((SimulatorRuleRepository) null))
    );
  }

  @Test
  void shouldReturnStandardizedErrorEnvelope() throws Exception {
    String content = mockMvc.perform(get("/api/v1/errors/boom"))
        .andExpect(status().isBadRequest())
        .andReturn()
        .getResponse()
        .getContentAsString();

    JsonNode actual = objectMapper.readTree(content);
    JsonNode snapshot;
    try (InputStream inputStream = new ClassPathResource("contracts/error-boom-snapshot.json").getInputStream()) {
      snapshot = objectMapper.readTree(inputStream);
    }

    assertThat(actual.path("code").asText()).isEqualTo(snapshot.path("code").asText());
    assertThat(actual.path("message").asText()).isEqualTo(snapshot.path("message").asText());
    assertThat(actual.path("path").asText()).isEqualTo(snapshot.path("path").asText());
    assertThat(actual.path("correlationId").asText()).isNotBlank();
    assertThat(actual.path("timestamp").asText()).isNotBlank();
  }
}
