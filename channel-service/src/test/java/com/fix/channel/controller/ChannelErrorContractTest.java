package com.fix.channel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class ChannelErrorContractTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

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
