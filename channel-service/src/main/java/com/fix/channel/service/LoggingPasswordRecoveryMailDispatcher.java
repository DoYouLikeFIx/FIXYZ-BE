package com.fix.channel.service;

import com.fix.common.logging.LogPiiMasking;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingPasswordRecoveryMailDispatcher implements PasswordRecoveryMailDispatcher {

  @Override
  public void dispatch(String email, String rawToken, Instant expiresAt) {
    log.info(
        "Password recovery email dispatch scheduled for email={} expiresAt={}",
        LogPiiMasking.REDACTED,
        expiresAt
    );
  }
}
