package com.fix.channel.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class ChannelSessionRequestLockTest {

  @Test
  void shouldSerializeSameSessionAcrossLockInstancesWhenRedisIsShared() throws Exception {
    Map<String, String> sharedRedisLocks = new ConcurrentHashMap<>();
    ChannelSessionRequestLock firstNodeLock = new TestChannelSessionRequestLock(sharedRedisLocks);
    TestChannelSessionRequestLock secondNodeLock = new TestChannelSessionRequestLock(
        sharedRedisLocks,
        new CountDownLatch(1)
    );
    CountDownLatch firstActionEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstAction = new CountDownLatch(1);
    AtomicInteger activeActions = new AtomicInteger();
    AtomicInteger maxConcurrentActions = new AtomicInteger();
    List<String> invocationOrder = new CopyOnWriteArrayList<>();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first = executor.submit(() -> firstNodeLock.executeLocked("session-301", () -> {
        invocationOrder.add("first");
        int current = activeActions.incrementAndGet();
        maxConcurrentActions.updateAndGet(existing -> Math.max(existing, current));
        firstActionEntered.countDown();
        assertThat(awaitQuietly(releaseFirstAction, 2)).isTrue();
        activeActions.decrementAndGet();
        return null;
      }));

      assertThat(firstActionEntered.await(1, TimeUnit.SECONDS)).isTrue();

      Future<?> second = executor.submit(() -> secondNodeLock.executeLocked("session-301", () -> {
        invocationOrder.add("second");
        int current = activeActions.incrementAndGet();
        maxConcurrentActions.updateAndGet(existing -> Math.max(existing, current));
        activeActions.decrementAndGet();
        return null;
      }));

      assertThat(secondNodeLock.awaitAcquireAttempted(1)).isTrue();
      assertThat(invocationOrder).containsExactly("first");
      assertThat(maxConcurrentActions.get()).isEqualTo(1);

      releaseFirstAction.countDown();

      first.get(2, TimeUnit.SECONDS);
      second.get(2, TimeUnit.SECONDS);

      assertThat(invocationOrder).containsExactly("first", "second");
      assertThat(sharedRedisLocks).isEmpty();
    } finally {
      executor.shutdownNow();
    }
  }

  private boolean awaitQuietly(CountDownLatch latch, int timeoutSeconds) {
    try {
      return latch.await(timeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for distributed lock test", ex);
    }
  }

  private static final class TestChannelSessionRequestLock extends ChannelSessionRequestLock {

    private final Map<String, String> sharedRedisLocks;
    private final CountDownLatch acquireAttempted;

    private TestChannelSessionRequestLock(Map<String, String> sharedRedisLocks) {
      this(sharedRedisLocks, null);
    }

    private TestChannelSessionRequestLock(Map<String, String> sharedRedisLocks, CountDownLatch acquireAttempted) {
      super(new StaticListableBeanFactory(
          Map.of("redisTemplate", new StringRedisTemplate())
      ).getBeanProvider(StringRedisTemplate.class));
      this.sharedRedisLocks = sharedRedisLocks;
      this.acquireAttempted = acquireAttempted;
    }

    @Override
    protected Long executeAcquire(
        StringRedisTemplate redisTemplate,
        String lockKey,
        String lockToken,
        java.time.Duration ttl
    ) {
      if (acquireAttempted != null) {
        acquireAttempted.countDown();
      }
      return sharedRedisLocks.putIfAbsent(lockKey, lockToken) == null ? 1L : 0L;
    }

    @Override
    protected Long executeRelease(StringRedisTemplate redisTemplate, String lockKey, String lockToken) {
      return sharedRedisLocks.remove(lockKey, lockToken) ? 1L : 0L;
    }

    private boolean awaitAcquireAttempted(int timeoutSeconds) throws InterruptedException {
      return acquireAttempted != null && acquireAttempted.await(timeoutSeconds, TimeUnit.SECONDS);
    }
  }
}
