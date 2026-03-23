package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class AdminApiRateLimitServiceTest {

  @Test
  void shouldUseDistinctRedisKeysForReplayAndReconciliationBuckets() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    List<String> capturedKeys = new ArrayList<>();
    when(redisTemplate.execute(any(), anyList(), any()))
        .thenAnswer(invocation -> {
          capturedKeys.addAll(invocation.getArgument(1));
          return 1L;
        });

    AdminApiRateLimitService service = new AdminApiRateLimitService(provider(redisTemplate));
    ReflectionTestUtils.setField(service, "windowSeconds", 60L);
    ReflectionTestUtils.setField(service, "defaultMaxAttempts", 20);
    ReflectionTestUtils.setField(service, "orderReplayMaxAttempts", 20);
    ReflectionTestUtils.setField(service, "orderReconciliationMaxAttempts", 20);

    service.enforceOrderReplay("admin-session-1");
    service.enforceOrderReconciliation("admin-session-1");

    assertThat(capturedKeys)
        .containsExactly(
            "ch:ratelimit:admin:endpoint:order-replay:session:admin-session-1",
            "ch:ratelimit:admin:endpoint:order-reconciliation:session:admin-session-1"
        );
  }

  private static ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate template) {
    return new ObjectProvider<>() {
      @Override
      public StringRedisTemplate getObject(Object... args) {
        if (template == null) {
          throw new IllegalStateException("No StringRedisTemplate available");
        }
        return template;
      }

      @Override
      public StringRedisTemplate getIfAvailable() {
        return template;
      }

      @Override
      public StringRedisTemplate getIfAvailable(Supplier<StringRedisTemplate> defaultSupplier) {
        return template != null ? template : defaultSupplier.get();
      }

      @Override
      public StringRedisTemplate getIfUnique() {
        return template;
      }

      @Override
      public StringRedisTemplate getIfUnique(Supplier<StringRedisTemplate> defaultSupplier) {
        return template != null ? template : defaultSupplier.get();
      }

      @Override
      public Iterator<StringRedisTemplate> iterator() {
        return template == null
            ? Collections.<StringRedisTemplate>emptyList().iterator()
            : Collections.singletonList(template).iterator();
      }

      @Override
      public void forEach(Consumer<? super StringRedisTemplate> action) {
        if (template != null) {
          action.accept(template);
        }
      }

      @Override
      public Stream<StringRedisTemplate> stream() {
        return template == null ? Stream.empty() : Stream.of(template);
      }

      @Override
      public Stream<StringRedisTemplate> orderedStream() {
        return stream();
      }
    };
  }
}
