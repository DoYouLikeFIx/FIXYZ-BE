package com.fix.channel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.channel.entity.ManualRecoveryQueueEntry;
import com.fix.channel.repository.ManualRecoveryQueueEntryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final Duration publishClaimTimeout;

  public ManualRecoveryQueueService(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository,
      ObjectMapper objectMapper,
      Clock clock,
      @Value("${order.session.recovery.manual-queue.publish-batch-size:100}") int publishBatchSize,
      @Value("${order.session.recovery.manual-queue.claim-timeout:5m}") Duration publishClaimTimeout
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.manualRecoveryQueueEntryRepository = manualRecoveryQueueEntryRepository;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.publishBatchSize = Math.max(1, publishBatchSize);
    this.publishClaimTimeout = publishClaimTimeout == null || publishClaimTimeout.isNegative()
        ? Duration.ZERO
        : publishClaimTimeout;
  }

  public void publishPendingEntries() {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      log.warn("Manual recovery queue Redis unavailable; leaving entries pending");
      return;
    }

    List<ManualRecoveryQueueEntry> pendingEntries;
    try {
      pendingEntries = manualRecoveryQueueEntryRepository.findByPublishedAtIsNullAndResolvedAtIsNullOrderByEnqueuedAtAscIdAsc(
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
    Instant now = Instant.now(clock);
    String claimToken = UUID.randomUUID().toString();
    int claimed = manualRecoveryQueueEntryRepository.claimPendingIfAvailable(
        entry.getId(),
        entry.getEnqueuedAt(),
        claimToken,
        now,
        now.minus(publishClaimTimeout)
    );
    if (claimed == 0) {
      return;
    }
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
        releaseClaim(entry, claimToken);
        return;
      }

      int updated = manualRecoveryQueueEntryRepository.markPublishedIfClaimed(
          entry.getId(),
          entry.getEnqueuedAt(),
          claimToken,
          now
      );
      if (updated == 0) {
        log.warn(
            "Manual recovery queue payload published to Redis but claimed row was not acknowledged: sessionId={}, clOrdId={}",
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
      releaseClaim(entry, claimToken);
    } catch (RuntimeException ex) {
      log.warn(
          "Failed to publish manual recovery queue payload to Redis: sessionId={}, clOrdId={}",
          entry.getOrderSessionId(),
          entry.getClOrdId(),
          ex
      );
      releaseClaim(entry, claimToken);
    }
  }

  private String dedupeKey(ManualRecoveryQueueEntry entry) {
    return MANUAL_RECOVERY_PUBLISHED_KEY_PREFIX + entry.getId() + ":" + entry.getEnqueuedAt();
  }

  private String serialize(ManualRecoveryQueueEntry entry) throws JsonProcessingException {
    return objectMapper.writeValueAsString(new ManualRecoveryTaskPayload(
        entry.getId(),
        entry.getEnqueuedAt().toString(),
        entry.getOrderSessionId(),
        entry.getClOrdId(),
        entry.getAttemptCount(),
        entry.getReason()
    ));
  }

  private record ManualRecoveryTaskPayload(
      Long entryId,
      String enqueuedAt,
      String orderSessionId,
      String clOrdId,
      int attemptCount,
      String reason
  ) {
  }

  private void releaseClaim(ManualRecoveryQueueEntry entry, String claimToken) {
    manualRecoveryQueueEntryRepository.releaseClaimIfMatches(
        entry.getId(),
        entry.getEnqueuedAt(),
        claimToken
    );
  }

  @Transactional
  public void resolveIfPresent(
      String orderSessionId,
      String resolvedBy,
      String resolution,
      Instant resolvedAt
  ) {
    manualRecoveryQueueEntryRepository.findByOrderSessionIdAndResolvedAtIsNull(orderSessionId)
        .ifPresent(entry -> {
          int updated = manualRecoveryQueueEntryRepository.markResolvedIfUnresolved(
              entry.getId(),
              entry.getEnqueuedAt(),
              resolvedBy,
              resolution,
              resolvedAt
          );
          if (updated == 0) {
            log.warn(
                "Manual recovery queue entry resolve skipped because state changed concurrently: sessionId={}, enqueuedAt={}",
                orderSessionId,
                entry.getEnqueuedAt()
            );
            return;
          }
          log.info(
              "Manual recovery queue entry resolved: sessionId={}, resolution={}, resolvedBy={}",
              orderSessionId,
              resolution,
              resolvedBy
          );
        });
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
