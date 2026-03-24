package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminAuditActionMapperTest {

  private AdminAuditActionMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new AdminAuditActionMapper();
  }

  @Test
  void shouldExposeFixedCanonicalVocabulary() {
    assertThat(mapper.supportedCanonicalActions()).containsExactlyInAnyOrder(
        "LOGIN_SUCCESS",
        "LOGIN_FAIL",
        "LOGOUT",
        "ADMIN_FORCE_LOGOUT",
        "ORDER_SESSION_CREATE",
        "ORDER_OTP_SUCCESS",
        "ORDER_OTP_FAIL",
        "ORDER_EXECUTE",
        "ORDER_CANCEL",
        "ORDER_RECOVERY",
        "ORDER_RECONCILIATION",
        "MANUAL_REPLAY",
        "TOTP_ENROLL",
        "TOTP_CONFIRM"
    );
  }

  @Test
  void shouldMapStoredActionsToCanonicalActions() {
    assertThat(mapper.canonicalize("AUTH_LOGIN_SUCCESS")).isEqualTo("LOGIN_SUCCESS");
    assertThat(mapper.canonicalize("AUTH_LOGIN_FAILURE")).isEqualTo("LOGIN_FAIL");
    assertThat(mapper.canonicalize("ORDER_SESSION_OTP_FAILED")).isEqualTo("ORDER_OTP_FAIL");
    assertThat(mapper.canonicalize("ORDER_SESSION_OTP_REPLAYED")).isEqualTo("ORDER_OTP_FAIL");
    assertThat(mapper.canonicalize("ORDER_SESSION_RECOVERY_ATTEMPT")).isEqualTo("ORDER_RECOVERY");
    assertThat(mapper.canonicalize("ORDER_SESSION_RECONCILIATION")).isEqualTo("ORDER_RECONCILIATION");
    assertThat(mapper.canonicalize("MANUAL_REPLAY")).isEqualTo("MANUAL_REPLAY");
    assertThat(mapper.canonicalize("AUTH_TOTP_ENROLLMENT_CONFIRMED")).isEqualTo("TOTP_CONFIRM");
  }

  @Test
  void shouldResolveStoredActionsForCanonicalFilter() {
    assertThat(mapper.storedActionsForCanonical("order_otp_fail"))
        .containsExactly("ORDER_SESSION_OTP_FAILED", "ORDER_SESSION_OTP_RATE_LIMITED", "ORDER_SESSION_OTP_REPLAYED");
    assertThat(mapper.storedActionsForCanonical("order_recovery"))
        .containsExactly("ORDER_SESSION_RECOVERY_ATTEMPT");
    assertThat(mapper.storedActionsForCanonical("order_reconciliation"))
        .containsExactly("ORDER_SESSION_RECONCILIATION");
    assertThat(mapper.storedActionsForCanonical("manual_replay"))
        .containsExactly("MANUAL_REPLAY");
  }
}
