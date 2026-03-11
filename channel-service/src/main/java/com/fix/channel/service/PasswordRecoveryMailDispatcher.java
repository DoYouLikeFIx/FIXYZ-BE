package com.fix.channel.service;

import java.time.Instant;

public interface PasswordRecoveryMailDispatcher {

  void dispatch(String email, String rawToken, Instant expiresAt);
}
