package com.fix.channel.vo;

import java.time.Instant;

public class TotpEnrollResult {

  private final String manualEntryKey;
  private final String qrUri;
  private final String enrollmentToken;
  private final Instant expiresAt;

  private TotpEnrollResult(String manualEntryKey, String qrUri, String enrollmentToken, Instant expiresAt) {
    this.manualEntryKey = manualEntryKey;
    this.qrUri = qrUri;
    this.enrollmentToken = enrollmentToken;
    this.expiresAt = expiresAt;
  }

  public static TotpEnrollResult of(String manualEntryKey, String qrUri, String enrollmentToken, Instant expiresAt) {
    return new TotpEnrollResult(manualEntryKey, qrUri, enrollmentToken, expiresAt);
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
