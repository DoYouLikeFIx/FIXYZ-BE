package com.fix.channel.repository;

import com.fix.channel.entity.AuditLog;
import com.fix.channel.repository.custom.AuditLogCustomRepository;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, AuditLogCustomRepository {

  long deleteByCreatedAtBefore(Instant cutoff);
}
