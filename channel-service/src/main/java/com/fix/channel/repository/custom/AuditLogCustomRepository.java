package com.fix.channel.repository.custom;

import com.fix.channel.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogCustomRepository {

  Page<AuditLog> findAdminAuditLogs(
      Pageable pageable,
      Instant from,
      Instant to,
      Long memberId,
      List<String> actions
  );
}