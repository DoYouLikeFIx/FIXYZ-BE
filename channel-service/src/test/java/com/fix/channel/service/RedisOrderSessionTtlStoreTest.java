package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisOrderSessionTtlStoreTest {

  @Test
  void shouldRejectActivationWhenRedisScriptDoesNotAcknowledgeWrite() {
    RedisOrderSessionTtlStore store =
        new NullActivationResultRedisOrderSessionTtlStore(
            provider(new StringRedisTemplate()),
            Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC)
        );

    assertThatThrownBy(() -> store.activate("sess-1", Instant.parse("2026-03-12T00:10:00Z")))
        .isInstanceOf(BusinessException.class)
        .hasMessage("order session cache activation failed");
  }

  @Test
  void shouldRejectActivationWhenExpiryIsAlreadyInThePast() {
    RedisOrderSessionTtlStore store = new RedisOrderSessionTtlStore(
        provider(new StringRedisTemplate()),
        Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC)
    );

    assertThatThrownBy(() -> store.activate("sess-1", Instant.parse("2026-03-11T23:59:59Z")))
        .isInstanceOf(BusinessException.class)
        .hasMessage("order session expiration must be in the future");
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

  private static class NullActivationResultRedisOrderSessionTtlStore extends RedisOrderSessionTtlStore {

    NullActivationResultRedisOrderSessionTtlStore(
        ObjectProvider<StringRedisTemplate> redisTemplateProvider,
        Clock clock
    ) {
      super(redisTemplateProvider, clock);
    }

    @Override
    protected Long executeActivation(StringRedisTemplate redisTemplate, String orderSessionId, long ttlMillis) {
      return null;
    }
  }
}
