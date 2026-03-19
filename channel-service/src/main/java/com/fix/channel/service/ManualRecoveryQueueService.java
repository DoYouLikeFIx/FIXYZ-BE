package com.fix.channel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.ManualRecoveryQueueEntry;
import com.fix.channel.repository.ManualRecoveryQueueEntryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ManualRecoveryQueueService {

  private static final String MANUAL_RECOVERY_QUEUE_KEY = "ch:manual-recovery:queue";
  private static final String MANUAL_RECOVERY_PUBLISHED_KEY_PREFIX = "ch:manual-recovery:published:";
  private static final Duration MANUAL_RECOVERY_PUBLISH_DEDUPE_TTL = Duration.ofDays(30);
  private static final DefaultRedisScript<Long> PUBLISH_IF_ABSENT_SCRIPT = createPublishIfAbsentScript();

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final int publishBatchSize;

  public ManualRecoveryQueueService(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${order.session.recovery.manual-queue.publish-batch-size:100}") int publishBatchSize
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.manualRecoveryQueueEntryRepository = manualRecoveryQueueEntryRepository;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.publishBatchSize = Math.max(1, publishBatchSize);
  }

  public void publishPendingEntries() {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      log.warn("Manual recovery queue Redis unavailable; leaving entries pending");
      return;
    }

    List<ManualRecoveryQueueEntry> pendingEntries;
    try {
      pendingEntries = manualRecoveryQueueEntryRepository.findByPublishedAtIsNullOrderByEnqueuedAtAscIdAsc(
          PageRequest.of(0, publishBatchSize)
      );
    } catch (RuntimeException ex) {
      log.warn("Failed to load pending manual recovery queue entries", ex);
      return;
    }

    if (pendingEntries.isEmpty()) {
      return;
    }

    for (ManualRecoveryQueueEntry entry : pendingEntries) {
      publishSingleEntry(redisTemplate, entry);
    }
  }

  private void publishSingleEntry(StringRedisTemplate redisTemplate, ManualRecoveryQueueEntry entry) {
    try {
      String payload = serialize(entry);
      Long publishResult = redisTemplate.execute(
          PUBLISH_IF_ABSENT_SCRIPT,
          List.of(dedupeKey(entry), MANUAL_RECOVERY_QUEUE_KEY),
          entry.getOrderSessionId(),
          String.valueOf(MANUAL_RECOVERY_PUBLISH_DEDUPE_TTL.toMillis()),
          payload
      );
      if (publishResult == null) {
        log.warn(
            "Manual recovery queue publish returned no result: sessionId={}, clOrdId={}",
            entry.getOrderSessionId(),
            entry.getClOrdId()
        );
        return;
      }

      int updated = manualRecoveryQueueEntryRepository.markPublishedIfPending(
          entry.getId(),
          entry.getEnqueuedAt(),
          Instant.now(clock)
      );
      if (updated == 0 && publishResult.longValue() == 1L) {
        log.warn(
            "Manual recovery queue payload published to Redis but pending row was not acknowledged: sessionId={}, clOrdId={}",
            entry.getOrderSessionId(),
            entry.getClOrdId()
        );
      }
    } catch (JsonProcessingException ex) {
      log.warn(
          "Failed to serialize manual recovery queue payload: sessionId={}, clOrdId={}",
          entry.getOrderSessionId(),
          entry.getClOrdId(),
          ex
      );
    } catch (RuntimeException ex) {
      log.warn(
          "Failed to publish manual recovery queue payload to Redis: sessionId={}, clOrdId={}",
          entry.getOrderSessionId(),
          entry.getClOrdId(),
          ex
      );
    }
  }

  private String dedupeKey(ManualRecoveryQueueEntry entry) {
    return MANUAL_RECOVERY_PUBLISHED_KEY_PREFIX + entry.getId() + ":" + entry.getEnqueuedAt();
  }

  private String serialize(ManualRecoveryQueueEntry entry) throws JsonProcessingException {
    return objectMapper.writeValueAsString(new ManualRecoveryTaskPayload(
        entry.getEnqueuedAt().toString(),
        entry.getOrderSessionId(),
        entry.getClOrdId(),
        entry.getAttemptCount(),
        entry.getReason()
    ));
  }

  private record ManualRecoveryTaskPayload(
      String enqueuedAt,
      String orderSessionId,
      String clOrdId,
      int attemptCount,
      String reason
  ) {
  }

  private static DefaultRedisScript<Long> createPublishIfAbsentScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText("""
        if redis.call('EXISTS', KEYS[1]) == 1 then
          return 0
        end
        redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
        redis.call('RPUSH', KEYS[2], ARGV[3])
        return 1
        """);
    script.setResultType(Long.class);
    return script;
  }
}
