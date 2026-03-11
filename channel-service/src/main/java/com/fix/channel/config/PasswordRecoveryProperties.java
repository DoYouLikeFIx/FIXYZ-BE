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
