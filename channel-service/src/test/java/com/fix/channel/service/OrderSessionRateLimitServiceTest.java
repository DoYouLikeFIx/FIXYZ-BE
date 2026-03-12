package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class OrderSessionRateLimitServiceTest {

  @Test
  void shouldFailClosedWhenRedisIsUnavailable() {
    OrderSessionRateLimitService service = new OrderSessionRateLimitService(provider(null));
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "maxAttempts", 10);
    ReflectionTestUtils.setField(service, "windowSeconds", 60L);

    assertThatThrownBy(() -> service.enforceCreateRateLimit(301L))
        .isInstanceOf(BusinessException.class)
        .hasMessage("order session rate limit unavailable");
  }

  @Test
  void shouldFailClosedWhenIncrementScriptReturnsNull() {
    OrderSessionRateLimitService service =
        new NullIncrementResultOrderSessionRateLimitService(provider(new StringRedisTemplate()));
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "maxAttempts", 10);
    ReflectionTestUtils.setField(service, "windowSeconds", 60L);

    assertThatThrownBy(() -> service.enforceCreateRateLimit(301L))
        .isInstanceOf(BusinessException.class)
        .hasMessage("order session rate limit unavailable");
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

  private static class NullIncrementResultOrderSessionRateLimitService extends OrderSessionRateLimitService {

    NullIncrementResultOrderSessionRateLimitService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
      super(redisTemplateProvider);
    }

    @Override
    protected Long executeIncrement(StringRedisTemplate redisTemplate, String key) {
      return null;
    }
  }
}
