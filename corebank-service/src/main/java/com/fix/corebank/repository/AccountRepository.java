package com.fix.corebank.repository;

import com.fix.corebank.entity.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
  Optional<Account> findByAccountNo(String accountNo);
}
