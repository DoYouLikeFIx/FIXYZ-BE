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

  List<ManualRecoveryQueueEntry> findByPublishedAtIsNullOrderByEnqueuedAtAscIdAsc(Pageable pageable);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE ManualRecoveryQueueEntry entry
         SET entry.publishedAt = :publishedAt
       WHERE entry.id = :id
         AND entry.publishedAt IS NULL
         AND entry.enqueuedAt = :enqueuedAt
      """)
  int markPublishedIfPending(
      @Param("id") Long id,
      @Param("enqueuedAt") Instant enqueuedAt,
      @Param("publishedAt") Instant publishedAt
  );
}
