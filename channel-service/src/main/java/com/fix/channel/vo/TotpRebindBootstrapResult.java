package com.fix.channel.vo;

import java.time.Instant;

public class TotpRebindBootstrapResult {

  private final String rebindToken;
  private final String manualEntryKey;
  private final String qrUri;
  private final String enrollmentToken;
  private final Instant expiresAt;

  private TotpRebindBootstrapResult(
      String rebindToken,
      String manualEntryKey,
      String qrUri,
      String enrollmentToken,
      Instant expiresAt
  ) {
    this.rebindToken = rebindToken;
    this.manualEntryKey = manualEntryKey;
    this.qrUri = qrUri;
    this.enrollmentToken = enrollmentToken;
    this.expiresAt = expiresAt;
  }

  public static TotpRebindBootstrapResult of(
      String rebindToken,
      String manualEntryKey,
      String qrUri,
      String enrollmentToken,
      Instant expiresAt
  ) {
    return new TotpRebindBootstrapResult(rebindToken, manualEntryKey, qrUri, enrollmentToken, expiresAt);
  }

  public String getRebindToken() {
    return rebindToken;
  }

  public String getManualEntryKey() {
    return manualEntryKey;
  }

  public String getQrUri() {
    return qrUri;
  }

  public String getEnrollmentToken() {
    return enrollmentToken;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
