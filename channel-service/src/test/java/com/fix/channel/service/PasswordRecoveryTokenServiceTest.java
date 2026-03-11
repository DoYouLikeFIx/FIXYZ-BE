package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.channel.config.PasswordRecoveryProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class PasswordRecoveryTokenServiceTest {

  @Test
  void shouldGenerateCandidateHashesForCurrentAndPreviousPeppers() {
    PasswordRecoveryProperties properties = properties();
    PasswordRecoveryTokenService tokenService = new PasswordRecoveryTokenService(properties);

    List<PasswordRecoveryTokenService.TokenHash> candidates = tokenService.candidateHashes("raw-reset-token");

    assertThat(candidates).hasSize(2);
    assertThat(candidates).extracting(PasswordRecoveryTokenService.TokenHash::pepperVersion)
        .containsExactly((short) 7, (short) 6);
    assertThat(candidates).extracting(PasswordRecoveryTokenService.TokenHash::hash)
        .doesNotHaveDuplicates();
  }

  @Test
  void shouldValidateChallengeSignaturesDeterministically() {
    PasswordRecoveryProperties properties = properties();
    PasswordRecoveryTokenService tokenService = new PasswordRecoveryTokenService(properties);

    String payload = "payload";
    String signature = tokenService.sign(payload);

    assertThat(tokenService.signaturesMatch(payload, signature)).isTrue();
    assertThat(tokenService.signaturesMatch(payload, signature + "x")).isFalse();
  }

  private PasswordRecoveryProperties properties() {
    PasswordRecoveryProperties properties = new PasswordRecoveryProperties();
    properties.getToken().setCurrentPepperVersion(7);
    properties.getToken().setCurrentPepper("current-pepper");
    properties.getToken().setPreviousPepperVersion(6);
    properties.getToken().setPreviousPepper("previous-pepper");
    properties.getToken().setChallengeSigningSecret("challenge-signing-secret");
    return properties;
  }
}
