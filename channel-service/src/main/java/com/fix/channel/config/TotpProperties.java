package com.fix.channel.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.totp")
public class TotpProperties {

  private String issuer = "FIXYZ";
  private String enrollmentPepper = "dev-totp-enrollment-pepper";
  private String secretStore = "in-memory";
  private final Vault vault = new Vault();

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getEnrollmentPepper() {
    return enrollmentPepper;
  }

  public void setEnrollmentPepper(String enrollmentPepper) {
    this.enrollmentPepper = enrollmentPepper;
  }

  public String getSecretStore() {
    return secretStore;
  }

  public void setSecretStore(String secretStore) {
    this.secretStore = secretStore;
  }

  public Vault getVault() {
    return vault;
  }

  public static class Vault {

    private String baseUrl = "http://localhost:8200";
    private String token = "root-token";
    private String mount = "secret";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }

    public String getMount() {
      return mount;
    }

    public void setMount(String mount) {
      this.mount = mount;
    }

    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
      this.readTimeout = readTimeout;
    }
  }
}
