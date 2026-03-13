package com.fix.channel.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.testsupport.OrderSessionTestFixture;
import com.fix.common.web.CommonHeaders;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OrderSessionTestFixture.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.session.store-type=none",
    "internal.secret=test-secret",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class ChannelErrorContractTest {

  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    WIRE_MOCK_SERVER.start();
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OrderSessionTestFixture orderSessionTestFixture;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("corebank.base-url", WIRE_MOCK_SERVER::baseUrl);
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK_SERVER.stop();
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

  @Test
  @WithMockUser(username = "qa-user")
  void shouldExposeMappedExternalErrorMetadataAtRealChannelBoundary() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    orderSessionTestFixture.reset();
    String orderSessionId = orderSessionTestFixture.createInitiatedSessionId(
        301L,
        1L,
        "123e4567-e89b-42d3-a456-426614174260",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.valueOf(2),
        BigDecimal.valueOf(70100),
        false,
        "RECENT_LOGIN_MFA",
        Instant.now().plusSeconds(600)
    );
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/internal/v1/orders"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(504)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "FEP-002",
                  "message": "Exchange connectivity timeout",
                  "path": "/internal/v1/orders",
                  "correlationId": "trace-core-timeout",
                  "userMessageKey": "error.fep.timeout",
                  "operatorCode": "TIMEOUT",
                  "details": {
                    "accountId": 1,
                    "symbol": "005930",
                    "requestedQty": 2
                  },
                  "timestamp": "2026-03-10T00:00:00Z"
                }
                """)));

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/execute", orderSessionId)
            .with(csrf())
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-timeout"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-timeout"))
        .andExpect(jsonPath("$.code").value("FEP-002"))
        .andExpect(jsonPath("$.message").value("Exchange connectivity timeout"))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions/" + orderSessionId + "/execute"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.timeout"))
        .andExpect(jsonPath("$.operatorCode").value("TIMEOUT"))
        .andExpect(jsonPath("$.details.symbol").value("005930"))
        .andExpect(jsonPath("$.details.requestedQty").value(2))
        .andExpect(jsonPath("$.details.accountId").doesNotExist())
        .andExpect(jsonPath("$.correlationId").value("trace-channel-timeout"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());

    WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/internal/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-channel-timeout")));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldRejectCanonicalExecuteWhenOrderSessionIsNotAuthorized() throws Exception {
    orderSessionTestFixture.reset();
    String orderSessionId = orderSessionTestFixture.createInitiatedSessionId(
        301L,
        1L,
        "123e4567-e89b-42d3-a456-426614174261",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.valueOf(2),
        BigDecimal.valueOf(70100),
        true,
        "ELEVATED_ORDER_RISK",
        Instant.now().plusSeconds(600)
    );

    mockMvc.perform(post("/api/v1/orders/sessions/{orderSessionId}/execute", orderSessionId)
            .with(csrf())
            .sessionAttr("AUTH_MEMBER_ID", 301L))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORD-009"))
        .andExpect(jsonPath("$.message").value("order session is not authorized for execution"))
        .andExpect(jsonPath("$.path").value("/api/v1/orders/sessions/" + orderSessionId + "/execute"));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldNotExposeLegacyOrderExecutionEndpoint() throws Exception {
    mockMvc.perform(post("/api/v1/orders")
            .with(csrf())
            .sessionAttr("AUTH_MEMBER_ID", 301L))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldForwardAuthMemberIdAndExposeBalanceAliasesForAccountPosition() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("symbol", equalTo("005930"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "accountId": 1,
                    "memberId": 301,
                    "symbol": "005930",
                    "quantity": 120.0000,
                    "availableQuantity": 120.0000,
                    "availableQty": 120.0000,
                    "balance": 1000000.0000,
                    "availableBalance": 1000000.0000,
                    "currency": "KRW",
                    "asOf": "2026-03-10T00:00:00Z"
                  }
                }
                """)));

    mockMvc.perform(get("/api/v1/accounts/{accountId}/positions", 1L)
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-position")
            .param("symbol", "005930"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-position"))
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.availableQuantity").value(120.0))
        .andExpect(jsonPath("$.data.availableQty").value(120.0))
        .andExpect(jsonPath("$.data.balance").value(1000000.0))
        .andExpect(jsonPath("$.data.availableBalance").value(1000000.0))
        .andExpect(jsonPath("$.data.currency").value("KRW"))
        .andExpect(jsonPath("$.data.asOf").value("2026-03-10T00:00:00Z"));

    WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("symbol", equalTo("005930"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-channel-position")));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldForwardSessionMemberIdAndExposePagedOrderHistory() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/internal/v1/accounts/1/orders"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("20"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "content": [
                      {
                        "symbol": "005930",
                        "symbolName": "삼성전자",
                        "side": "BUY",
                        "qty": 2.0000,
                        "unitPrice": 70100.0000,
                        "totalAmount": 140200.0000,
                        "status": "FILLED",
                        "clOrdId": "123e4567-e89b-42d3-a456-426614174320",
                        "createdAt": "2026-03-10T00:00:00Z"
                      }
                    ],
                    "totalElements": 1,
                    "totalPages": 1,
                    "number": 0,
                    "size": 20
                  }
                }
                """)));

    mockMvc.perform(get("/api/v1/accounts/{accountId}/orders", 1L)
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-history")
            .param("page", "0")
            .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-history"))
        .andExpect(jsonPath("$.data.content[0].symbol").value("005930"))
        .andExpect(jsonPath("$.data.content[0].symbolName").value("삼성전자"))
        .andExpect(jsonPath("$.data.content[0].qty").value(2.0))
        .andExpect(jsonPath("$.data.content[0].unitPrice").value(70100.0))
        .andExpect(jsonPath("$.data.content[0].totalAmount").value(140200.0))
        .andExpect(jsonPath("$.data.content[0].clOrdId").value("123e4567-e89b-42d3-a456-426614174320"))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.number").value(0))
        .andExpect(jsonPath("$.data.size").value(20));

    WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/orders"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("20"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-channel-history")));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldForwardSessionMemberIdAndExposeAccountSummary() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
            urlPathEqualTo("/internal/v1/accounts/1/summary"))
        .withQueryParam("memberId", equalTo("301"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "accountId": 1,
                    "memberId": 301,
                    "symbol": "",
                    "quantity": 0.0000,
                    "availableQuantity": 0.0000,
                    "availableQty": 0.0000,
                    "balance": 1000000.0000,
                    "availableBalance": 1000000.0000,
                    "currency": "KRW",
                    "asOf": "2026-03-10T00:00:00Z"
                  }
                }
                """)));

    mockMvc.perform(get("/api/v1/accounts/{accountId}/summary", 1L)
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-summary"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-summary"))
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.symbol").value(""))
        .andExpect(jsonPath("$.data.balance").value(1000000.0))
        .andExpect(jsonPath("$.data.availableBalance").value(1000000.0))
        .andExpect(jsonPath("$.data.currency").value("KRW"))
        .andExpect(jsonPath("$.data.asOf").value("2026-03-10T00:00:00Z"));

    WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/summary"))
        .withQueryParam("memberId", equalTo("301"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-channel-summary")));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldForwardSessionMemberIdAndExposeOwnedPositionList() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
            urlPathEqualTo("/internal/v1/accounts/1/positions/list"))
        .withQueryParam("memberId", equalTo("301"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": [
                    {
                      "accountId": 1,
                      "memberId": 301,
                      "symbol": "000660",
                      "quantity": 15.0000,
                      "availableQuantity": 15.0000,
                      "availableQty": 15.0000,
                      "balance": 98500000.0000,
                      "availableBalance": 98500000.0000,
                      "currency": "KRW",
                      "asOf": "2026-03-10T00:00:00Z"
                    },
                    {
                      "accountId": 1,
                      "memberId": 301,
                      "symbol": "005930",
                      "quantity": 120.0000,
                      "availableQuantity": 120.0000,
                      "availableQty": 120.0000,
                      "balance": 100000000.0000,
                      "availableBalance": 100000000.0000,
                      "currency": "KRW",
                      "asOf": "2026-03-10T00:01:00Z"
                    }
                  ]
                }
                """)));

    mockMvc.perform(get("/api/v1/accounts/{accountId}/positions/list", 1L)
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-position-list"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-position-list"))
        .andExpect(jsonPath("$.data[0].symbol").value("000660"))
        .andExpect(jsonPath("$.data[1].symbol").value("005930"))
        .andExpect(jsonPath("$.data[1].availableQuantity").value(120.0))
        .andExpect(jsonPath("$.data[1].availableQty").value(120.0));

    WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/positions/list"))
        .withQueryParam("memberId", equalTo("301"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-channel-position-list")));
  }

  @Test
  @WithMockUser(username = "admin-user", roles = "ADMIN")
  void shouldForwardAdminStatusTransitionAndExposeTransitionResponse() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.patch(urlPathEqualTo("/internal/v1/accounts/1/status"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "accountId": 1,
                    "memberId": 301,
                    "previousStatus": "ACTIVE",
                    "newStatus": "FROZEN",
                    "changed": true,
                    "eventId": 9001,
                    "reason": "risk-control",
                    "actor": "ops-admin",
                    "context": "ticket=FIX-43",
                    "asOf": "2026-03-10T00:00:00Z"
                  }
                }
                """)));

    mockMvc.perform(patch("/api/v1/admin/accounts/{accountId}/status", 1L)
            .with(csrf())
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-admin-transition")
            .contentType("application/json")
            .content("""
                {
                  "memberId": 301,
                  "status": "FROZEN",
                  "reason": "risk-control",
                  "actor": "ops-admin",
                  "context": "ticket=FIX-43"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-admin-transition"))
        .andExpect(jsonPath("$.data.accountId").value(1L))
        .andExpect(jsonPath("$.data.memberId").value(301L))
        .andExpect(jsonPath("$.data.previousStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.newStatus").value("FROZEN"))
        .andExpect(jsonPath("$.data.changed").value(true))
        .andExpect(jsonPath("$.data.eventId").value(9001))
        .andExpect(jsonPath("$.data.reason").value("risk-control"))
        .andExpect(jsonPath("$.data.actor").value("ops-admin"))
        .andExpect(jsonPath("$.data.context").value("ticket=FIX-43"))
        .andExpect(jsonPath("$.data.asOf").value("2026-03-10T00:00:00Z"));

    WIRE_MOCK_SERVER.verify(patchRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/status"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-channel-admin-transition")));
  }

  @Test
  @WithMockUser(username = "admin-user", roles = "ADMIN")
  void shouldExposeOwnershipErrorForAdminStatusTransitionBoundary() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.patch(urlPathEqualTo("/internal/v1/accounts/1/status"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(403)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "AUTH-005",
                  "message": "forbidden account ownership",
                  "path": "/internal/v1/accounts/1/status",
                  "correlationId": "trace-core-admin-ownership",
                  "timestamp": "2026-03-10T00:00:00Z"
                }
                """)));

    mockMvc.perform(patch("/api/v1/admin/accounts/{accountId}/status", 1L)
            .with(csrf())
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-admin-ownership")
            .contentType("application/json")
            .content("""
                {
                  "memberId": 301,
                  "status": "FROZEN",
                  "reason": "risk-control",
                  "actor": "ops-admin"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-admin-ownership"))
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("forbidden account ownership"))
        .andExpect(jsonPath("$.path").value("/api/v1/admin/accounts/1/status"))
        .andExpect(jsonPath("$.correlationId").value("trace-channel-admin-ownership"));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldRequireAdminRoleForAccountStatusTransitionBoundary() throws Exception {
    mockMvc.perform(patch("/api/v1/admin/accounts/{accountId}/status", 1L)
            .with(csrf())
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-admin-access-denied")
            .contentType("application/json")
            .content("""
                {
                  "memberId": 301,
                  "status": "FROZEN",
                  "reason": "risk-control",
                  "actor": "ops-admin"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-admin-access-denied"))
        .andExpect(jsonPath("$.code").value("AUTH-006"))
        .andExpect(jsonPath("$.message").value("Access denied."))
        .andExpect(jsonPath("$.path").value("/api/v1/admin/accounts/1/status"));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldReturnAuthRequiredWhenSessionDoesNotContainMemberId() throws Exception {
    mockMvc.perform(get("/api/v1/accounts/{accountId}/positions", 1L)
            .param("symbol", "005930"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("authentication required"));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldExposeOwnershipErrorForAccountPositionBoundary() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(403)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "AUTH-005",
                  "message": "forbidden account ownership",
                  "path": "/internal/v1/accounts/1/positions",
                  "correlationId": "trace-core-ownership",
                  "timestamp": "2026-03-10T00:00:00Z"
                }
                """)));

    mockMvc.perform(get("/api/v1/accounts/{accountId}/positions", 1L)
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-ownership")
            .param("symbol", "005930"))
        .andExpect(status().isForbidden())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-ownership"))
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("forbidden account ownership"))
        .andExpect(jsonPath("$.path").value("/api/v1/accounts/1/positions"))
        .andExpect(jsonPath("$.correlationId").value("trace-channel-ownership"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldExposeOwnershipErrorForAccountOrderHistoryBoundary() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/internal/v1/accounts/1/orders"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(403)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "AUTH-005",
                  "message": "forbidden account ownership",
                  "path": "/internal/v1/accounts/1/orders",
                  "correlationId": "trace-core-ownership-history",
                  "timestamp": "2026-03-10T00:00:00Z"
                }
                """)));

    mockMvc.perform(get("/api/v1/accounts/{accountId}/orders", 1L)
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-history-ownership")
            .param("page", "0")
            .param("size", "20"))
        .andExpect(status().isForbidden())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-history-ownership"))
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("forbidden account ownership"))
        .andExpect(jsonPath("$.path").value("/api/v1/accounts/1/orders"))
        .andExpect(jsonPath("$.correlationId").value("trace-channel-history-ownership"));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldNormalizeDependencyTimeoutToCore901AtAccountPositionBoundary() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(504)
            .withHeader("Content-Type", "text/plain")
            .withBody("upstream timeout")));

    mockMvc.perform(get("/api/v1/accounts/{accountId}/positions", 1L)
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-core-timeout")
            .param("symbol", "005930"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-core-timeout"))
        .andExpect(jsonPath("$.code").value("CORE-901"))
        .andExpect(jsonPath("$.message").value("Core dependency timeout"))
        .andExpect(jsonPath("$.path").value("/api/v1/accounts/1/positions"))
        .andExpect(jsonPath("$.correlationId").value("trace-channel-core-timeout"));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldNormalizeDependencyUnavailableToCore902AtAccountPositionBoundary() throws Exception {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withStatus(503)
            .withHeader("Content-Type", "text/plain")
            .withBody("service unavailable")));

    mockMvc.perform(get("/api/v1/accounts/{accountId}/positions", 1L)
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .header(CommonHeaders.X_CORRELATION_ID, "trace-channel-core-unavailable")
            .param("symbol", "005930"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-channel-core-unavailable"))
        .andExpect(jsonPath("$.code").value("CORE-902"))
        .andExpect(jsonPath("$.message").value("Core dependency unavailable"))
        .andExpect(jsonPath("$.path").value("/api/v1/accounts/1/positions"))
        .andExpect(jsonPath("$.correlationId").value("trace-channel-core-unavailable"));
  }

  @Test
  @WithMockUser(username = "qa-user")
  void shouldReturnValidationErrorForMalformedAccountOrderHistoryPagination() throws Exception {
    mockMvc.perform(get("/api/v1/accounts/{accountId}/orders", 1L)
            .sessionAttr("AUTH_MEMBER_ID", 301L)
            .param("page", "-1")
            .param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }
}
