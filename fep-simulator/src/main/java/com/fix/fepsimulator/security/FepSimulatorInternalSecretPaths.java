package com.fix.fepsimulator.security;

public final class FepSimulatorInternalSecretPaths {

  private FepSimulatorInternalSecretPaths() {
  }

  public static boolean requiresInternalSecret(String path) {
    return path != null && path.startsWith("/fep-internal/");
  }
}
