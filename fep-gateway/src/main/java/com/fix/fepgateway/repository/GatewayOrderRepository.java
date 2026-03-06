package com.fix.fepgateway.repository;

import com.fix.fepgateway.entity.GatewayOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayOrderRepository extends JpaRepository<GatewayOrder, Long> {
  Optional<GatewayOrder> findByClOrdId(String clOrdId);
}
