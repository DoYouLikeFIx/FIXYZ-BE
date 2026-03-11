package com.fix.corebank.repository;

import com.fix.corebank.entity.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, Long> {
  Optional<Account> findByAccountNo(String accountNo);

  Optional<Account> findByMemberId(Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id = :accountId")
  Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);
}
