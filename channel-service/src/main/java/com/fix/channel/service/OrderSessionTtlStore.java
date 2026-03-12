package com.fix.channel.service;

import java.time.Duration;
import java.time.Instant;

public interface OrderSessionTtlStore {

  void activate(String orderSessionId, Instant expiresAt);

  boolean isActive(String orderSessionId);

  void clear(String orderSessionId);

  Duration ttl();
}
