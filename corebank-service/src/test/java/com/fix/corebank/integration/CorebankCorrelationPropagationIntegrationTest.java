package com.fix.corebank.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.corebank.entity.Account;
import com.fix.corebank.entity.MemberEntity;
import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.MemberRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret"
})
@AutoConfigureMockMvc
class CorebankCorrelationPropagationIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final String CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174246";
  private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    WIRE_MOCK_SERVER.start();
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private PositionRepository positionRepository;

  @Autowired
  private MemberRepository memberRepository;

  private Long accountId;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("fep.gateway.base-url", WIRE_MOCK_SERVER::baseUrl);
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK_SERVER.stop();
  }

  @BeforeEach
  void setUp() {
    WIRE_MOCK_SERVER.resetAll();
    jdbcTemplate.update("DELETE FROM ledger_entry_refs");
    jdbcTemplate.update("DELETE FROM ledger_entries");
    jdbcTemplate.update("DELETE FROM journal_entries");
    jdbcTemplate.update("DELETE FROM executions");
    jdbcTemplate.update("DELETE FROM orders");
    positionRepository.deleteAllInBatch();
    accountRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();

    MemberEntity member = memberRepository.save(MemberEntity.of(900001L, "M-TRACE-900001", "trace-900001@fix.local"));
    Account account = accountRepository.save(Account.of(
        "900001000001",
        member.getId(),
        "KRW",
        BigDecimal.valueOf(100000000L),
        BigDecimal.valueOf(500L)
    ));
    accountId = account.getId();
    positionRepository.save(Position.of(accountId, "005930", BigDecimal.valueOf(120L), BigDecimal.valueOf(70000L)));
  }

  @Test
  void shouldForwardCorrelationAndTraceparentHeadersToFepGatewayThroughInternalApi() throws Exception {
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "fepOrderId": "FEP-KRX-%s",
                    "execType": "FILL",
                    "ordStatus": "FILLED",
                    "executedQty": 2,
                    "executedPrice": 70100,
                    "leavesQty": 0,
                    "transactTime": "2026-03-01T10:00:00Z"
                  },
                  "error": null
                }
                """.formatted(CL_ORD_ID, CL_ORD_ID))));

    mockMvc.perform(post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-propagation")
            .header(CommonHeaders.TRACEPARENT, TRACEPARENT)
            .param("accountId", accountId.toString())
            .param("clOrdId", CL_ORD_ID)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-propagation"))
        .andExpect(header().string(CommonHeaders.TRACEPARENT, TRACEPARENT))
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID))
        .andExpect(jsonPath("$.data.externalOrderId").value("FEP-KRX-" + CL_ORD_ID));

    WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-core-propagation"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(CL_ORD_ID)));
  }
}
