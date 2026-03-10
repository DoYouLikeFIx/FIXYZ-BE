package com.fix.fepgateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.fepgateway.filter.CorrelationIdFilter;
import com.fix.fepgateway.support.FepGatewayStandaloneMvcSupport;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class FepGatewayErrorContractTest {

  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    mockMvc = FepGatewayStandaloneMvcSupport.build(
        List.of(new CorrelationIdFilter()),
        new FepGatewayController()
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
