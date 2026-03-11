package com.fix.channel.repository;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.custom.OrderSessionCustomRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSessionRepository extends JpaRepository<OrderSession, Long>, OrderSessionCustomRepository {
  Optional<OrderSession> findByOrderSessionId(String orderSessionId);

  Optional<OrderSession> findByClOrdId(String clOrdId);

  long deleteByOrderSessionId(String orderSessionId);

  List<OrderSession> findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
      Collection<OrderSessionStatus> statuses,
      Instant cutoff,
      Pageable pageable
  );
}
