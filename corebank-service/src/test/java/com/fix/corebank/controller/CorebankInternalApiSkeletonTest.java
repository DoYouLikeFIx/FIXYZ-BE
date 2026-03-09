package com.fix.corebank.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.support.TestStubFepClient;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CorebankInternalApiSkeletonTest.StubFepClientConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_internal_api;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "internal.secret=test-secret"
})
class CorebankInternalApiSkeletonTest {

  private static final String CORE_CL_ORD_ID_1 = "123e4567-e89b-42d3-a456-426614174210";
  private static final String CORE_CL_ORD_ID_2 = "123e4567-e89b-42d3-a456-426614174211";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TestStubFepClient fepClient;

  @BeforeEach
  void setUp() {
    fepClient.reset();
  }

  @Test
  void shouldSupportInternalPortfolioAndOrderEndpoints() throws Exception {
    fepClient.setSubmitResult(new FepOrderResult(
        CORE_CL_ORD_ID_1,
        "FEP-KRX-" + CORE_CL_ORD_ID_1,
        FepExecType.FILL,
        FepOrdStatus.FILLED,
        2L,
        70100L,
        0L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null
    ));
    fepClient.setQueryResult(new FepOrderResult(
        CORE_CL_ORD_ID_1,
        "FEP-KRX-" + CORE_CL_ORD_ID_1,
        FepExecType.FILL,
        FepOrdStatus.FILLED,
        2L,
        70100L,
        0L,
        Instant.parse("2026-03-01T10:05:30Z"),
        Instant.parse("2026-03-01T10:10:00Z"),
        null
    ));

    mockMvc.perform(get("/internal/v1/portfolio")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("symbol", "005930"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.symbol").value("005930"));

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_1)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.status").value("FILLED"));

    mockMvc.perform(get("/internal/v1/orders/{clOrdId}/requery", CORE_CL_ORD_ID_1)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CORE_CL_ORD_ID_1))
        .andExpect(jsonPath("$.data.status").value("FILLED"));
  }

  @Test
  void shouldRejectFractionalOrderInputsBeforeCallingFepClient() throws Exception {
    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CORE_CL_ORD_ID_2)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.5000")
            .param("price", "70100.1000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @TestConfiguration
  static class StubFepClientConfig {

    @Bean
    @Primary
    TestStubFepClient testStubFepClient() {
      return new TestStubFepClient();
    }
  }
}
