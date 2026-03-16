package com.fix.channel.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_correlation;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.session.store-type=none",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class CorrelationIdFilterTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldGenerateCorrelationIdWhenHeaderMissing() throws Exception {
    mockMvc.perform(get("/api/v1/ping"))
        .andExpect(status().isOk())
        .andExpect(header().exists(CommonHeaders.X_CORRELATION_ID))
        .andExpect(header().exists(CommonHeaders.TRACEPARENT));
  }

  @Test
  void shouldPreserveProvidedCorrelationId() throws Exception {
    mockMvc.perform(get("/api/v1/ping").header(CommonHeaders.X_CORRELATION_ID, "corr-channel-001"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-channel-001"));
  }

  @Test
  void shouldPreserveProvidedTraceparent() throws Exception {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    mockMvc.perform(get("/api/v1/ping").header(CommonHeaders.TRACEPARENT, traceparent))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.TRACEPARENT, traceparent));
  }
}
