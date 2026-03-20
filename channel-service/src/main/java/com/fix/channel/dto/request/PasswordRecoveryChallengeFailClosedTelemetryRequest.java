package com.fix.channel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record PasswordRecoveryChallengeFailClosedTelemetryRequest(
    @NotBlank
    @Pattern(regexp = "unknown-version|kind-mismatch|malformed-payload|mixed-shape|clock-skew|validity-untrusted")
    String reason,
    @NotBlank
    @Pattern(regexp = "forgot-password-web|forgot-password-mobile")
    String surface,
    @PositiveOrZero
    Long challengeIssuedAtEpochMs
) {
}
