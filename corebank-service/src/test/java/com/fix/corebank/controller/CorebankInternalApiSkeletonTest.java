package com.fix.corebank.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    "spring.datasource.url=jdbc:h2:mem:core_internal_api;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "internal.secret=test-secret"
})
class CorebankInternalApiSkeletonTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldSupportInternalPortfolioAndOrderEndpoints() throws Exception {
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
            .param("clOrdId", "CORE-API-001")
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value("CORE-API-001"));

    mockMvc.perform(get("/internal/v1/orders/CORE-API-001/requery")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value("CORE-API-001"));
  }
}
