package com.fix.fepgateway.repository;

import com.fix.fepgateway.entity.GatewaySession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewaySessionRepository extends JpaRepository<GatewaySession, Long> {
  Optional<GatewaySession> findBySessionId(String sessionId);
}
