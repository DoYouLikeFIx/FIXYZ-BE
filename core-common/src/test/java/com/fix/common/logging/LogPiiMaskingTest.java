package com.fix.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogPiiMaskingTest {

  @Test
  void shouldMaskAccountNumbersWithCanonicalLogFormat() {
    assertThat(LogPiiMasking.maskAccountNumber("110123456789")).isEqualTo("110-****-6789");
  }

  @Test
  void shouldRedactSecretBearingKeyValuePairsInFreeformText() {
    String sanitized = LogPiiMasking.sanitizeText(
        "accountNumber=110123456789, password=Abcd1234!, otp=654321, sessionToken=session-123, "
            + "authorization=Bearer abc.def.ghi, cookie=session-123, "
            + "Authorization: Bearer abc.def.ghi, Cookie: SESSION=session-123; JSESSIONID=legacy-456"
    );

    assertThat(sanitized)
        .contains("accountNumber=110-****-6789")
        .contains("password=[REDACTED]")
        .contains("otp=[REDACTED]")
        .contains("sessionToken=[REDACTED]")
        .contains("authorization=[REDACTED]")
        .contains("cookie=[REDACTED]")
        .contains("Authorization: [REDACTED]")
        .contains("Cookie: [REDACTED]")
        .doesNotContain("110123456789")
        .doesNotContain("Abcd1234!")
        .doesNotContain("654321")
        .doesNotContain("session-123")
        .doesNotContain("abc.def.ghi")
        .doesNotContain("legacy-456");
  }

  @Test
  void shouldRedactStructuredJsonSensitiveFields() {
    String sanitized = LogPiiMasking.sanitizeText(
        """
            {"accountNumber":"110123456789","accountNo":"220987654321","password":"Abcd1234!",
            "authorization":"Bearer abc.def.ghi","cookie":"session-123","otpCode":"654321"}
            """
    );

    assertThat(sanitized)
        .contains("\"accountNumber\":\"110-****-6789\"")
        .contains("\"accountNo\":\"220-****-4321\"")
        .contains("\"password\":\"[REDACTED]\"")
        .contains("\"authorization\":\"[REDACTED]\"")
        .contains("\"cookie\":\"[REDACTED]\"")
        .contains("\"otpCode\":\"[REDACTED]\"")
        .doesNotContain("110123456789")
        .doesNotContain("220987654321")
        .doesNotContain("Abcd1234!")
        .doesNotContain("abc.def.ghi")
        .doesNotContain("session-123")
        .doesNotContain("654321");
  }

  @Test
  void shouldRedactNumericJsonSecretsAndMaskNumericJsonAccountNumbers() {
    String sanitized = LogPiiMasking.sanitizeText(
        """
            {"accountNumber":110123456789,"otpCode":654321,"sessionToken":1234567890,"token":777777,"email":"recover.user@fixyz.com"}
            """
    );

    assertThat(sanitized)
        .contains("\"accountNumber\":\"110-****-6789\"")
        .contains("\"otpCode\":\"[REDACTED]\"")
        .contains("\"sessionToken\":\"[REDACTED]\"")
        .contains("\"token\":\"[REDACTED]\"")
        .contains("\"email\":\"[REDACTED]\"")
        .doesNotContain("110123456789")
        .doesNotContain("654321")
        .doesNotContain("1234567890")
        .doesNotContain("777777")
        .doesNotContain("recover.user@fixyz.com");
  }

  @Test
  void shouldRedactCompositeAuthorizationAndCookieAssignmentsWithoutLeakingSuffixes() {
    String sanitized = LogPiiMasking.sanitizeText(
        "authorization=Digest username=alice, response=abc123, retry=1, "
            + "cookie=session-123, note=hello, payload={\"accountNumber\":110123456789}"
    );

    assertThat(sanitized)
        .contains("authorization=[REDACTED]")
        .contains("retry=1")
        .contains("cookie=[REDACTED]")
        .contains("note=hello")
        .contains("\"accountNumber\":\"110-****-6789\"")
        .doesNotContain("alice")
        .doesNotContain("abc123")
        .doesNotContain("session-123")
        .doesNotContain("110123456789");
  }

  @Test
  void shouldRedactTokenEmailAndIpFieldsInFreeformText() {
    String sanitized = LogPiiMasking.sanitizeText(
        "token=reset-token-123, email=recover.user@fixyz.com, clientIp=198.51.100.123, "
            + "{\"token\":\"reset-token-123\",\"email\":\"recover.user@fixyz.com\"}"
    );

    assertThat(sanitized)
        .contains("token=[REDACTED]")
        .contains("email=[REDACTED]")
        .contains("clientIp=198.51.100.0")
        .contains("\"token\":\"[REDACTED]\"")
        .contains("\"email\":\"[REDACTED]\"")
        .doesNotContain("reset-token-123")
        .doesNotContain("recover.user@fixyz.com")
        .doesNotContain("198.51.100.123");
  }

  @Test
  void shouldNotMaskUnlabeledNumericIdentifiers() {
    String sanitized = LogPiiMasking.sanitizeText("memberId=202603190001, orderRef=123456789012");

    assertThat(sanitized)
        .contains("memberId=202603190001")
        .contains("orderRef=123456789012");
  }

  @Test
  void shouldRedactAuditTargetIdsForSessionTargets() {
    assertThat(LogPiiMasking.sanitizeAuditTargetId("SESSION", "raw-session-id-123"))
        .isEqualTo(LogPiiMasking.REDACTED);
  }

  @Test
  void shouldRedactAuditTargetIdsForSecretBearingRecoveryTargets() {
    assertThat(LogPiiMasking.sanitizeAuditTargetId("PASSWORD_RECOVERY", "proof-token-123"))
        .isEqualTo(LogPiiMasking.REDACTED);
    assertThat(LogPiiMasking.sanitizeAuditTargetId("TOTP", "proof-token-123"))
        .isEqualTo(LogPiiMasking.REDACTED);
  }

  @Test
  void shouldPreserveTraceableRecoveryTargetIdsWhenTheyAreNotOpaqueSecrets() {
    assertThat(LogPiiMasking.sanitizeAuditTargetId("PASSWORD_RECOVERY", "12345")).isEqualTo("12345");
    assertThat(LogPiiMasking.sanitizeAuditTargetId("TOTP", "M-PII-MFA-701")).isEqualTo("M-PII-MFA-701");
    assertThat(LogPiiMasking.sanitizeAuditTargetId("TOTP", "sessional-user-123")).isEqualTo("sessional-user-123");
    assertThat(LogPiiMasking.sanitizeAuditTargetId("TOTP", "proofreading-run-42")).isEqualTo("proofreading-run-42");
  }
}
