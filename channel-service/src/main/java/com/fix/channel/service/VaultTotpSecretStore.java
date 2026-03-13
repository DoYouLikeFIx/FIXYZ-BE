package com.fix.channel.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.config.TotpProperties;
import com.fix.channel.entity.Member;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@ConditionalOnProperty(name = "auth.totp.secret-store", havingValue = "vault")
public class VaultTotpSecretStore implements TotpSecretStore {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final String mount;

  public VaultTotpSecretStore(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      Clock clock,
      TotpProperties properties
  ) {
    TotpProperties.Vault vault = properties.getVault();
    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(Timeout.of(vault.getConnectTimeout()))
        .setResponseTimeout(Timeout.of(vault.getReadTimeout()))
        .build();

    this.restClient = restClientBuilder
        .baseUrl(vault.getBaseUrl())
        .requestFactory(new HttpComponentsClientHttpRequestFactory(
            HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().build())
                .build()
        ))
        .defaultHeader("X-Vault-Token", vault.getToken())
        .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
        .build();
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.mount = vault.getMount();
  }

  @Override
  public PendingTotpSecret getOrCreatePendingSecret(Member member, String loginToken, Instant expiresAt) {
    Optional<PendingTotpSecret> existing = findPendingSecret(member, loginToken);
    if (existing.isPresent()) {
      return existing.get();
    }

    PendingTotpSecret pendingSecret = new PendingTotpSecret(TotpService.generateRandomManualEntryKey(), expiresAt);
    Map<String, Object> payload = new HashMap<>();
    payload.put("secret", pendingSecret.manualEntryKey());
    if (pendingSecret.expiresAt() != null) {
      payload.put("expiresAt", pendingSecret.expiresAt().toString());
    }
    write(pathForPending(member, loginToken), payload);
    return pendingSecret;
  }

  @Override
  public Optional<PendingTotpSecret> findPendingSecret(Member member, String loginToken) {
    return read(pathForPending(member, loginToken))
        .flatMap(data -> {
          String secret = asString(data.get("secret"));
          Instant expiresAt = parseInstant(asString(data.get("expiresAt")));
          if (secret == null || secret.isBlank()) {
            return Optional.empty();
          }
          if (expiresAt != null && !expiresAt.isAfter(Instant.now(clock))) {
            delete(pathForPending(member, loginToken));
            return Optional.empty();
          }
          return Optional.of(new PendingTotpSecret(secret, expiresAt));
        });
  }

  @Override
  public Optional<String> findActiveSecret(Member member) {
    return read(pathForActive(member)).map(data -> asString(data.get("secret")));
  }

  @Override
  public void promotePendingSecret(Member member, String loginToken) {
    PendingTotpSecret pendingSecret = findPendingSecret(member, loginToken)
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_TOKEN_EXPIRED, "login token expired or invalid"));
    write(pathForActive(member), Map.of("secret", pendingSecret.manualEntryKey()));
    delete(pathForPending(member, loginToken));
  }

  @Override
  public void saveActiveSecret(Member member, String manualEntryKey) {
    write(pathForActive(member), Map.of("secret", manualEntryKey));
  }

  private Optional<Map<String, Object>> read(String path) {
    try {
      VaultResponse response = restClient.get()
          .uri(URI.create(path))
          .retrieve()
          .body(VaultResponse.class);
      if (response == null || response.data() == null || response.data().data() == null) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.convertValue(response.data().data(), MAP_TYPE));
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        return Optional.empty();
      }
      throw vaultFailure("vault read failed", ex);
    } catch (Exception ex) {
      throw vaultFailure("vault read failed", ex);
    }
  }

  private void write(String path, Map<String, Object> data) {
    try {
      restClient.post()
          .uri(URI.create(path))
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("data", data))
          .retrieve()
          .toBodilessEntity();
    } catch (Exception ex) {
      throw vaultFailure("vault write failed", ex);
    }
  }

  private void delete(String path) {
    try {
      restClient.delete()
          .uri(URI.create(metadataPath(path)))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        return;
      }
      throw vaultFailure("vault delete failed", ex);
    } catch (Exception ex) {
      throw vaultFailure("vault delete failed", ex);
    }
  }

  private String pathForActive(Member member) {
    return "/v1/" + mount + "/data/fix/member/" + member.getId() + "/totp-secret";
  }

  private String pathForPending(Member member, String loginToken) {
    return "/v1/" + mount + "/data/fix/member/" + member.getId() + "/totp-pending/" + fingerprint(loginToken);
  }

  private String metadataPath(String dataPath) {
    return dataPath.replace("/data/", "/metadata/");
  }

  private String fingerprint(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "vault fingerprint hashing failed", ex);
    }
  }

  private Instant parseInstant(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return null;
    }
    return Instant.parse(rawValue);
  }

  private String asString(Object value) {
    return value instanceof String text ? text : null;
  }

  private BusinessException vaultFailure(String message, Exception ex) {
    return new BusinessException(ErrorCode.INTERNAL_ERROR, message, ex);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record VaultResponse(VaultData data) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record VaultData(Map<String, Object> data) {
  }
}
