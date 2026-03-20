package com.fix.channel.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.password-recovery")
public class PasswordRecoveryProperties {

  private final Forgot forgot = new Forgot();
  private final Challenge challenge = new Challenge();
  private final Reset reset = new Reset();
  private final Token token = new Token();
  private final Timing timing = new Timing();
  private final Cleanup cleanup = new Cleanup();

  public Forgot getForgot() {
    return forgot;
  }

  public Challenge getChallenge() {
    return challenge;
  }

  public Reset getReset() {
    return reset;
  }

  public Token getToken() {
    return token;
  }

  public Timing getTiming() {
    return timing;
  }

  public Cleanup getCleanup() {
    return cleanup;
  }

  public static class Forgot {
    private final RateLimit ip = new RateLimit(5, Duration.ofMinutes(1));
    private final RateLimit email = new RateLimit(3, Duration.ofMinutes(15));
    private Duration mailCooldown = Duration.ofMinutes(5);
    private int challengeRequiredAfterAttempts = 1;

    public RateLimit getIp() {
      return ip;
    }

    public RateLimit getEmail() {
      return email;
    }

    public Duration getMailCooldown() {
      return mailCooldown;
    }

    public void setMailCooldown(Duration mailCooldown) {
      this.mailCooldown = mailCooldown;
    }

    public int getChallengeRequiredAfterAttempts() {
      return challengeRequiredAfterAttempts;
    }

    public void setChallengeRequiredAfterAttempts(int challengeRequiredAfterAttempts) {
      this.challengeRequiredAfterAttempts = challengeRequiredAfterAttempts;
    }
  }

  public static class Challenge {
    private final RateLimit ip = new RateLimit(5, Duration.ofMinutes(1));
    private final RateLimit email = new RateLimit(3, Duration.ofMinutes(10));
    private final RateLimit global = new RateLimit(60, Duration.ofMinutes(1));
    private int ttlSeconds = 300;
    private String type = "proof-of-work";
    private boolean v2Enabled = false;
    private int cohortPercentage = 0;
    private String cohortSalt = "dev-password-recovery-challenge-cohort-salt";
    private boolean deterministicOverrideEnabled = false;
    private String deterministicOverrideHeader = "X-Fixyz-Recovery-Challenge-Mode";
    private String observabilitySecret = "dev-password-recovery-observability-secret";
    private int difficultyBits = 18;

    public RateLimit getIp() {
      return ip;
    }

    public RateLimit getEmail() {
      return email;
    }

    public RateLimit getGlobal() {
      return global;
    }

    public int getTtlSeconds() {
      return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
      this.ttlSeconds = ttlSeconds;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public boolean isV2Enabled() {
      return v2Enabled;
    }

    public void setV2Enabled(boolean v2Enabled) {
      this.v2Enabled = v2Enabled;
    }

    public int getCohortPercentage() {
      return cohortPercentage;
    }

    public void setCohortPercentage(int cohortPercentage) {
      this.cohortPercentage = cohortPercentage;
    }

    public String getCohortSalt() {
      return cohortSalt;
    }

    public void setCohortSalt(String cohortSalt) {
      this.cohortSalt = cohortSalt;
    }

    public boolean isDeterministicOverrideEnabled() {
      return deterministicOverrideEnabled;
    }

    public void setDeterministicOverrideEnabled(boolean deterministicOverrideEnabled) {
      this.deterministicOverrideEnabled = deterministicOverrideEnabled;
    }

    public String getDeterministicOverrideHeader() {
      return deterministicOverrideHeader;
    }

    public void setDeterministicOverrideHeader(String deterministicOverrideHeader) {
      this.deterministicOverrideHeader = deterministicOverrideHeader;
    }

    public String getObservabilitySecret() {
      return observabilitySecret;
    }

    public void setObservabilitySecret(String observabilitySecret) {
      this.observabilitySecret = observabilitySecret;
    }

    public int getDifficultyBits() {
      return difficultyBits;
    }

    public void setDifficultyBits(int difficultyBits) {
      this.difficultyBits = difficultyBits;
    }
  }

  public static class Reset {
    private final RateLimit ip = new RateLimit(10, Duration.ofMinutes(5));
    private final RateLimit token = new RateLimit(5, Duration.ofMinutes(15));
    private final RateLimit global = new RateLimit(60, Duration.ofMinutes(1));
    private Duration tokenTtl = Duration.ofMinutes(15);

    public RateLimit getIp() {
      return ip;
    }

    public RateLimit getToken() {
      return token;
    }

    public RateLimit getGlobal() {
      return global;
    }

    public Duration getTokenTtl() {
      return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
      this.tokenTtl = tokenTtl;
    }
  }

  public static class Token {
    private int currentPepperVersion = 2;
    private String currentPepper = "dev-current-pepper";
    private int previousPepperVersion = 1;
    private String previousPepper = "dev-previous-pepper";
    private String challengeSigningSecret = "dev-password-recovery-signing-secret";

    public int getCurrentPepperVersion() {
      return currentPepperVersion;
    }

    public void setCurrentPepperVersion(int currentPepperVersion) {
      this.currentPepperVersion = currentPepperVersion;
    }

    public String getCurrentPepper() {
      return currentPepper;
    }

    public void setCurrentPepper(String currentPepper) {
      this.currentPepper = currentPepper;
    }

    public int getPreviousPepperVersion() {
      return previousPepperVersion;
    }

    public void setPreviousPepperVersion(int previousPepperVersion) {
      this.previousPepperVersion = previousPepperVersion;
    }

    public String getPreviousPepper() {
      return previousPepper;
    }

    public void setPreviousPepper(String previousPepper) {
      this.previousPepper = previousPepper;
    }

    public String getChallengeSigningSecret() {
      return challengeSigningSecret;
    }

    public void setChallengeSigningSecret(String challengeSigningSecret) {
      this.challengeSigningSecret = challengeSigningSecret;
    }
  }

  public static class Timing {
    private final Delay forgot = new Delay(400, 50);
    private final Delay reset = new Delay(120, 20);

    public Delay getForgot() {
      return forgot;
    }

    public Delay getReset() {
      return reset;
    }
  }

  public static class Cleanup {
    private Duration cadence = Duration.ofMinutes(15);
    private Duration retention = Duration.ofDays(30);
    private int batchSize = 500;
    private int maxBatchesPerRun = 8;
    private int maxRunSeconds = 20;
    private long backlogAlertThreshold = 10_000L;

    public Duration getCadence() {
      return cadence;
    }

    public void setCadence(Duration cadence) {
      this.cadence = cadence;
    }

    public Duration getRetention() {
      return retention;
    }

    public void setRetention(Duration retention) {
      this.retention = retention;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public int getMaxBatchesPerRun() {
      return maxBatchesPerRun;
    }

    public void setMaxBatchesPerRun(int maxBatchesPerRun) {
      this.maxBatchesPerRun = maxBatchesPerRun;
    }

    public int getMaxRunSeconds() {
      return maxRunSeconds;
    }

    public void setMaxRunSeconds(int maxRunSeconds) {
      this.maxRunSeconds = maxRunSeconds;
    }

    public long getBacklogAlertThreshold() {
      return backlogAlertThreshold;
    }

    public void setBacklogAlertThreshold(long backlogAlertThreshold) {
      this.backlogAlertThreshold = backlogAlertThreshold;
    }
  }

  public static class Delay {
    private long floorMs;
    private long jitterMaxMs;

    public Delay() {
    }

    public Delay(long floorMs, long jitterMaxMs) {
      this.floorMs = floorMs;
      this.jitterMaxMs = jitterMaxMs;
    }

    public long getFloorMs() {
      return floorMs;
    }

    public void setFloorMs(long floorMs) {
      this.floorMs = floorMs;
    }

    public long getJitterMaxMs() {
      return jitterMaxMs;
    }

    public void setJitterMaxMs(long jitterMaxMs) {
      this.jitterMaxMs = jitterMaxMs;
    }
  }

  public static class RateLimit {
    private int maxAttempts;
    private Duration window;

    public RateLimit() {
    }

    public RateLimit(int maxAttempts, Duration window) {
      this.maxAttempts = maxAttempts;
      this.window = window;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public Duration getWindow() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }
  }
}
