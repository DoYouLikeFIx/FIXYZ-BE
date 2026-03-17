package com.fix.channel.repository;

import com.fix.channel.entity.AuditLog;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  long deleteByCreatedAtBefore(Instant cutoff);
}
