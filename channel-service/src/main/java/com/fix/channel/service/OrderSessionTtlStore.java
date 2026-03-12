package com.fix.channel.service;

import java.util.Optional;

public interface OrderSessionTtlStore {

  void activate(String orderSessionId, String initialStatus);

  Optional<Long> remainingSeconds(String orderSessionId);

  void clear(String orderSessionId);

  long ttlSeconds();
}
