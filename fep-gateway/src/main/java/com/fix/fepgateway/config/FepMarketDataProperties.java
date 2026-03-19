package com.fix.fepgateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fep.marketdata")
public class FepMarketDataProperties {

  private String provider = "NONE";
  private String sourceMode = "REPLAY";
  private final Kis kis = new Kis();

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getSourceMode() {
    return sourceMode;
  }

  public void setSourceMode(String sourceMode) {
    this.sourceMode = sourceMode;
  }

  public Kis getKis() {
    return kis;
  }

  public boolean isKisLiveModeEnabled() {
    return equalsIgnoreCase(provider, "KIS") && equalsIgnoreCase(sourceMode, "LIVE");
  }

  private static boolean equalsIgnoreCase(String left, String right) {
    return left != null && left.equalsIgnoreCase(right);
  }

  public static class Kis {
    private String env = "paper";
    private String appKey;
    private String appSecret;
    private final Ws ws = new Ws();

    public String getEnv() {
      return env;
    }

    public void setEnv(String env) {
      this.env = env;
    }

    public String getAppKey() {
      return appKey;
    }

    public void setAppKey(String appKey) {
      this.appKey = appKey;
    }

    public String getAppSecret() {
      return appSecret;
    }

    public void setAppSecret(String appSecret) {
      this.appSecret = appSecret;
    }

    public Ws getWs() {
      return ws;
    }
  }

  public static class Ws {
    private String trId = "H0STCNT0";
    private String custtype = "P";
    private List<String> symbols = new ArrayList<>(List.of("005930"));

    public String getTrId() {
      return trId;
    }

    public void setTrId(String trId) {
      this.trId = trId;
    }

    public String getCusttype() {
      return custtype;
    }

    public void setCusttype(String custtype) {
      this.custtype = custtype;
    }

    public List<String> getSymbols() {
      return symbols;
    }

    public void setSymbols(List<String> symbols) {
      this.symbols = symbols == null ? new ArrayList<>() : new ArrayList<>(symbols);
    }
  }
}
