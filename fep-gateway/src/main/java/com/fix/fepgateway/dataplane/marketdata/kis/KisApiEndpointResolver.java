package com.fix.fepgateway.dataplane.marketdata.kis;

public final class KisApiEndpointResolver {

  public static final String REAL_REST_BASE_URL = "https://openapi.koreainvestment.com:9443";
  public static final String PAPER_REST_BASE_URL = "https://openapivts.koreainvestment.com:29443";
  public static final String REAL_WS_BASE_URL = "ws://ops.koreainvestment.com:21000";
  public static final String PAPER_WS_BASE_URL = "ws://ops.koreainvestment.com:31000";

  private KisApiEndpointResolver() {
  }

  public static String resolveRestBaseUrl(String env) {
    if (isPaperEnvironment(env)) {
      return PAPER_REST_BASE_URL;
    }
    if (isRealEnvironment(env)) {
      return REAL_REST_BASE_URL;
    }
    throw new IllegalArgumentException("unsupported KIS environment: " + env);
  }

  public static String resolveWebSocketBaseUrl(String env) {
    if (isPaperEnvironment(env)) {
      return PAPER_WS_BASE_URL;
    }
    if (isRealEnvironment(env)) {
      return REAL_WS_BASE_URL;
    }
    throw new IllegalArgumentException("unsupported KIS environment: " + env);
  }

  private static boolean isPaperEnvironment(String env) {
    return env != null && ("paper".equalsIgnoreCase(env) || "demo".equalsIgnoreCase(env));
  }

  private static boolean isRealEnvironment(String env) {
    return env != null && "real".equalsIgnoreCase(env);
  }
}
