package com.fix.channel.repository;

import com.fix.channel.entity.ManualRecoveryQueueEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ManualRecoveryQueueEntryRepository extends JpaRepository<ManualRecoveryQueueEntry, Long> {
  Optional<ManualRecoveryQueueEntry> findByOrderSessionId(String orderSessionId);

  Optional<ManualRecoveryQueueEntry> findByOrderSessionIdAndResolvedAtIsNull(String orderSessionId);

  List<ManualRecoveryQueueEntry> findByPublishedAtIsNullAndResolvedAtIsNullOrderByEnqueuedAtAscIdAsc(Pageable pageable);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE ManualRecoveryQueueEntry entry
         SET entry.attemptCount = :attemptCount,
             entry.reason = :reason,
             entry.enqueuedAt = :nextEnqueuedAt,
             entry.publishedAt = NULL,
             entry.publishClaimToken = NULL,
             entry.publishClaimedAt = NULL,
             entry.resolvedBy = NULL,
             entry.resolution = NULL,
             entry.resolvedAt = NULL
       WHERE entry.id = :id
         AND entry.enqueuedAt = :currentEnqueuedAt
         AND entry.resolvedAt IS NULL
      """)
  int refreshIfPending(
      @Param("id") Long id,
      @Param("currentEnqueuedAt") Instant currentEnqueuedAt,
      @Param("attemptCount") int attemptCount,
      @Param("reason") String reason,
      @Param("nextEnqueuedAt") Instant nextEnqueuedAt
  );

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE ManualRecoveryQueueEntry entry
         SET entry.attemptCount = :attemptCount,
             entry.reason = :reason,
             entry.enqueuedAt = :nextEnqueuedAt,
             entry.publishedAt = NULL,
             entry.publishClaimToken = NULL,
             entry.publishClaimedAt = NULL,
             entry.resolvedBy = NULL,
             entry.resolution = NULL,
             entry.resolvedAt = NULL
       WHERE entry.id = :id
         AND entry.enqueuedAt = :currentEnqueuedAt
         AND entry.resolvedAt = :currentResolvedAt
      """)
  int refreshIfResolved(
      @Param("id") Long id,
      @Param("currentEnqueuedAt") Instant currentEnqueuedAt,
      @Param("currentResolvedAt") Instant currentResolvedAt,
      @Param("attemptCount") int attemptCount,
      @Param("reason") String reason,
      @Param("nextEnqueuedAt") Instant nextEnqueuedAt
  );

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE ManualRecoveryQueueEntry entry
         SET entry.resolvedBy = :resolvedBy,
             entry.resolution = :resolution,
             entry.resolvedAt = :resolvedAt,
             entry.publishClaimToken = NULL,
             entry.publishClaimedAt = NULL
       WHERE entry.id = :id
         AND entry.enqueuedAt = :currentEnqueuedAt
         AND entry.resolvedAt IS NULL
      """)
  int markResolvedIfUnresolved(
      @Param("id") Long id,
      @Param("currentEnqueuedAt") Instant currentEnqueuedAt,
      @Param("resolvedBy") String resolvedBy,
      @Param("resolution") String resolution,
      @Param("resolvedAt") Instant resolvedAt
  );

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE ManualRecoveryQueueEntry entry
         SET entry.publishedAt = :publishedAt
       WHERE entry.id = :id
         AND entry.publishedAt IS NULL
         AND entry.resolvedAt IS NULL
         AND entry.enqueuedAt = :enqueuedAt
      """)
  int markPublishedIfPending(
      @Param("id") Long id,
      @Param("enqueuedAt") Instant enqueuedAt,
      @Param("publishedAt") Instant publishedAt
  );

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE ManualRecoveryQueueEntry entry
         SET entry.publishClaimToken = :claimToken,
             entry.publishClaimedAt = :claimedAt
       WHERE entry.id = :id
         AND entry.publishedAt IS NULL
         AND entry.resolvedAt IS NULL
         AND entry.enqueuedAt = :enqueuedAt
         AND (
           entry.publishClaimToken IS NULL
           OR entry.publishClaimedAt IS NULL
           OR entry.publishClaimedAt < :staleBefore
         )
      """)
  int claimPendingIfAvailable(
      @Param("id") Long id,
      @Param("enqueuedAt") Instant enqueuedAt,
      @Param("claimToken") String claimToken,
      @Param("claimedAt") Instant claimedAt,
      @Param("staleBefore") Instant staleBefore
  );

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE ManualRecoveryQueueEntry entry
         SET entry.publishedAt = :publishedAt,
             entry.publishClaimToken = NULL,
             entry.publishClaimedAt = NULL
       WHERE entry.id = :id
         AND entry.publishedAt IS NULL
         AND entry.resolvedAt IS NULL
         AND entry.enqueuedAt = :enqueuedAt
         AND entry.publishClaimToken = :claimToken
      """)
  int markPublishedIfClaimed(
      @Param("id") Long id,
      @Param("enqueuedAt") Instant enqueuedAt,
      @Param("claimToken") String claimToken,
      @Param("publishedAt") Instant publishedAt
  );

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE ManualRecoveryQueueEntry entry
         SET entry.publishClaimToken = NULL,
             entry.publishClaimedAt = NULL
       WHERE entry.id = :id
         AND entry.publishedAt IS NULL
         AND entry.resolvedAt IS NULL
         AND entry.enqueuedAt = :enqueuedAt
         AND entry.publishClaimToken = :claimToken
      """)
  int releaseClaimIfMatches(
      @Param("id") Long id,
      @Param("enqueuedAt") Instant enqueuedAt,
      @Param("claimToken") String claimToken
  );
}
