package com.fix.corebank.repository;

import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.custom.OrderCustomRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderCustomRepository {
  Optional<Order> findByClOrdId(String clOrdId);
}
