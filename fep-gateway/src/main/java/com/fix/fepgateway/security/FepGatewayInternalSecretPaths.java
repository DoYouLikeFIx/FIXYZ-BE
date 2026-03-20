package com.fix.fepgateway.security;

public final class FepGatewayInternalSecretPaths {

  private FepGatewayInternalSecretPaths() {
  }

  public static boolean requiresInternalSecret(String path) {
    return path != null && (path.startsWith("/fep/") || path.startsWith("/fep-internal/"));
  }
}
