package com.fix.corebank.repository;

import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.custom.OrderCustomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderCustomRepository {
  Optional<Order> findByClOrdId(String clOrdId);

  Page<Order> findByAccountId(Long accountId, Pageable pageable);
}
