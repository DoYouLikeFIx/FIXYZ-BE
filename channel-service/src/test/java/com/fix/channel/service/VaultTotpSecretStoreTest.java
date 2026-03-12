package com.fix.channel.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.config.TotpProperties;
import com.fix.channel.entity.Member;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class VaultTotpSecretStoreTest {

  private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);

  private WireMockServer wireMockServer;
  private VaultTotpSecretStore vaultTotpSecretStore;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
    vaultTotpSecretStore = new VaultTotpSecretStore(
        RestClient.builder(),
        new ObjectMapper(),
        FIXED_CLOCK,
        properties("http://127.0.0.1:" + wireMockServer.port(), "vault-test-token")
    );
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void shouldCreatePendingSecretViaVaultKvV2Contract() {
    Member member = member();
    String loginToken = "login-token-001";
    Instant expiresAt = Instant.parse("2026-03-12T00:05:00Z");
    String pendingPath = pendingPath(member, loginToken);

    wireMockServer.stubFor(get(urlEqualTo(pendingPath))
        .willReturn(aResponse().withStatus(404)));
    wireMockServer.stubFor(post(urlEqualTo(pendingPath))
        .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));

    TotpSecretStore.PendingTotpSecret pendingSecret =
        vaultTotpSecretStore.getOrCreatePendingSecret(member, loginToken, expiresAt);

    assertThat(pendingSecret.manualEntryKey()).matches("[A-Z2-7]{32}");
    assertThat(pendingSecret.expiresAt()).isEqualTo(expiresAt);
    wireMockServer.verify(getRequestedFor(urlEqualTo(pendingPath))
        .withHeader("X-Vault-Token", equalTo("vault-test-token")));
    wireMockServer.verify(postRequestedFor(urlEqualTo(pendingPath))
        .withHeader("X-Vault-Token", equalTo("vault-test-token"))
        .withRequestBody(matchingJsonPath("$.data.secret", equalTo(pendingSecret.manualEntryKey())))
        .withRequestBody(matchingJsonPath("$.data.expiresAt", equalTo(expiresAt.toString()))));
  }

  @Test
  void shouldReuseExistingPendingSecretWithoutRewritingVault() {
    Member member = member();
    String loginToken = "login-token-002";
    Instant expiresAt = Instant.parse("2026-03-12T00:05:00Z");
    String pendingPath = pendingPath(member, loginToken);

    wireMockServer.stubFor(get(urlEqualTo(pendingPath))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "data": {
                    "data": {
                      "secret": "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
                      "expiresAt": "2026-03-12T00:05:00Z"
                    }
                  }
                }
                """)));

    TotpSecretStore.PendingTotpSecret pendingSecret =
        vaultTotpSecretStore.getOrCreatePendingSecret(member, loginToken, expiresAt.plusSeconds(60));

    assertThat(pendingSecret.manualEntryKey()).isEqualTo("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");
    assertThat(pendingSecret.expiresAt()).isEqualTo(expiresAt);
    wireMockServer.verify(1, getRequestedFor(urlEqualTo(pendingPath)));
    wireMockServer.verify(0, postRequestedFor(urlEqualTo(pendingPath)));
  }

  @Test
  void shouldPromotePendingSecretToActiveAndDeletePendingMetadata() {
    Member member = member();
    String loginToken = "login-token-003";
    String pendingPath = pendingPath(member, loginToken);
    String activePath = activePath(member);
    String metadataPath = pendingMetadataPath(member, loginToken);

    wireMockServer.stubFor(get(urlEqualTo(pendingPath))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "data": {
                    "data": {
                      "secret": "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
                      "expiresAt": "2026-03-12T00:05:00Z"
                    }
                  }
                }
                """)));
    wireMockServer.stubFor(post(urlEqualTo(activePath))
        .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));
    wireMockServer.stubFor(delete(urlEqualTo(metadataPath))
        .willReturn(aResponse().withStatus(204)));
    wireMockServer.stubFor(get(urlEqualTo(activePath))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "data": {
                    "data": {
                      "secret": "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
                    }
                  }
                }
                """)));

    vaultTotpSecretStore.promotePendingSecret(member, loginToken);

    assertThat(vaultTotpSecretStore.findActiveSecret(member))
        .contains("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");
    wireMockServer.verify(postRequestedFor(urlEqualTo(activePath))
        .withHeader("X-Vault-Token", equalTo("vault-test-token"))
        .withRequestBody(matchingJsonPath("$.data.secret", equalTo("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"))));
    wireMockServer.verify(deleteRequestedFor(urlEqualTo(metadataPath))
        .withHeader("X-Vault-Token", equalTo("vault-test-token")));
  }

  @Test
  void shouldFailClosedWhenVaultReadFails() {
    Member member = member();
    String loginToken = "login-token-004";
    String pendingPath = pendingPath(member, loginToken);

    wireMockServer.stubFor(get(urlEqualTo(pendingPath))
        .willReturn(aResponse().withStatus(500).withBody("vault unavailable")));

    assertThatThrownBy(() -> vaultTotpSecretStore.findPendingSecret(member, loginToken))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
          assertThat(businessException.getMessage()).isEqualTo("vault read failed");
        });
  }

  private TotpProperties properties(String baseUrl, String token) {
    TotpProperties properties = new TotpProperties();
    properties.setSecretStore("vault");
    properties.getVault().setBaseUrl(baseUrl);
    properties.getVault().setToken(token);
    properties.getVault().setMount("secret");
    return properties;
  }

  private Member member() {
    Member member = Member.registerUser("M-VAULT-001", "vault.user@fixyz.com", "encoded-password", "Vault User");
    ReflectionTestUtils.setField(member, "id", 42L);
    return member;
  }

  private String activePath(Member member) {
    return "/v1/secret/data/fix/member/" + member.getId() + "/totp-secret";
  }

  private String pendingPath(Member member, String loginToken) {
    return "/v1/secret/data/fix/member/" + member.getId() + "/totp-pending/" + fingerprint(loginToken);
  }

  private String pendingMetadataPath(Member member, String loginToken) {
    return "/v1/secret/metadata/fix/member/" + member.getId() + "/totp-pending/" + fingerprint(loginToken);
  }

  private String fingerprint(String loginToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(loginToken.trim().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
