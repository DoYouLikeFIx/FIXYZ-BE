package com.fix.channel.repository;

import com.fix.channel.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetToken t where t.memberId = :memberId and t.activeSlot = 1")
  List<PasswordResetToken> findActiveByMemberIdForUpdate(@Param("memberId") Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetToken t where t.tokenHash in :tokenHashes")
  List<PasswordResetToken> findByTokenHashesForUpdate(@Param("tokenHashes") Collection<String> tokenHashes);
}
