package com.fix.corebank.security;

public final class CorebankInternalSecretPaths {

  private CorebankInternalSecretPaths() {
  }

  public static boolean requiresInternalSecret(String path) {
    return path != null && path.startsWith("/internal/");
  }
}
