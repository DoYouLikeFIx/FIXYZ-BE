package com.fix.channel.service;

import java.util.Optional;

public interface OrderSessionTtlStore {

  void activate(String orderSessionId);

  Optional<Long> remainingSeconds(String orderSessionId);

  void clear(String orderSessionId);

  long ttlSeconds();
}
