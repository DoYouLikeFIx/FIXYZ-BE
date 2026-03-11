package com.fix.channel.repository;

import com.fix.channel.entity.PasswordResetToken;
import com.fix.channel.entity.PasswordResetTokenTerminalReason;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetToken t where t.memberId = :memberId and t.activeSlot = 1")
  List<PasswordResetToken> findActiveByMemberIdForUpdate(@Param("memberId") Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetToken t where t.tokenHash in :tokenHashes")
  List<PasswordResetToken> findByTokenHashesForUpdate(@Param("tokenHashes") Collection<String> tokenHashes);

  @Query("""
      select count(t) from PasswordResetToken t
      where t.activeSlot = 1 and t.expiresAt < :referenceTime
      """)
  long countExpiredActiveTokens(@Param("referenceTime") java.time.Instant referenceTime);

  @Query("""
      select count(t) from PasswordResetToken t
      where t.activeSlot is null and t.terminalizedAt < :retentionCutoff
      """)
  long countTerminalPurgeEligibleTokens(@Param("retentionCutoff") java.time.Instant retentionCutoff);

  @Query(
      value = """
          select id
          from password_reset_tokens
          where active_slot = 1
            and expires_at < :referenceTime
          order by expires_at asc, id asc
          limit :limit
          """,
      nativeQuery = true
  )
  List<Long> findExpiredActiveIdsForCleanup(
      @Param("referenceTime") java.time.Instant referenceTime,
      @Param("limit") int limit
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update PasswordResetToken t
      set t.activeSlot = null,
          t.terminalReason = :terminalReason,
          t.terminalizedAt = :terminalizedAt,
          t.updatedAt = :terminalizedAt
      where t.id in :ids
        and t.activeSlot = 1
        and t.expiresAt < :referenceTime
      """)
  int terminalizeExpiredTokens(
      @Param("ids") Collection<Long> ids,
      @Param("referenceTime") java.time.Instant referenceTime,
      @Param("terminalReason") PasswordResetTokenTerminalReason terminalReason,
      @Param("terminalizedAt") java.time.Instant terminalizedAt
  );

  @Query(
      value = """
          select id
          from password_reset_tokens
          where active_slot is null
            and terminalized_at < :retentionCutoff
          order by terminalized_at asc, expires_at asc, id asc
          limit :limit
          """,
      nativeQuery = true
  )
  List<Long> findTerminalPurgeCandidateIds(
      @Param("retentionCutoff") java.time.Instant retentionCutoff,
      @Param("limit") int limit
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      delete from PasswordResetToken t
      where t.id in :ids
        and t.activeSlot is null
        and t.terminalizedAt < :retentionCutoff
      """)
  int deleteTerminalizedTokensByIds(
      @Param("ids") Collection<Long> ids,
      @Param("retentionCutoff") java.time.Instant retentionCutoff
  );
}
