package com.fix.channel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

@JsonTest
@Import(JacksonConfig.class)
class JacksonConfigTest {

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void serializesInstantsAsIso8601AndOmitsNullValues() throws Exception {
    SamplePayload payload = new SamplePayload("channel", null, Instant.parse("2026-03-16T01:02:03Z"));

    String json = objectMapper.writeValueAsString(payload);

    assertThat(json).contains("\"name\":\"channel\"");
    assertThat(json).contains("\"createdAt\":\"2026-03-16T01:02:03Z\"");
    assertThat(json).doesNotContain("optional");
  }

  @Test
  void ignoresUnknownPropertiesWhenReadingJson() throws Exception {
    SamplePayload payload = objectMapper.readValue(
        """
            {
              "name": "channel",
              "createdAt": "2026-03-16T01:02:03Z",
              "ignored": "value"
            }
            """,
        SamplePayload.class
    );

    assertThat(payload.name()).isEqualTo("channel");
    assertThat(payload.optional()).isNull();
    assertThat(payload.createdAt()).isEqualTo(Instant.parse("2026-03-16T01:02:03Z"));
  }

  private record SamplePayload(String name, String optional, Instant createdAt) {
  }
}
