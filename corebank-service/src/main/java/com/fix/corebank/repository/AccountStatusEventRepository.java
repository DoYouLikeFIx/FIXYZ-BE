package com.fix.corebank.repository;

import com.fix.corebank.entity.AccountStatusEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountStatusEventRepository extends JpaRepository<AccountStatusEvent, Long> {
  List<AccountStatusEvent> findByAccountIdOrderByIdDesc(Long accountId);
}
