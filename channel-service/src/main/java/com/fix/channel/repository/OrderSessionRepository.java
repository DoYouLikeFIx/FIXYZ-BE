package com.fix.channel.repository;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.repository.custom.OrderSessionCustomRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSessionRepository extends JpaRepository<OrderSession, Long>, OrderSessionCustomRepository {
  Optional<OrderSession> findByClOrdId(String clOrdId);
}
