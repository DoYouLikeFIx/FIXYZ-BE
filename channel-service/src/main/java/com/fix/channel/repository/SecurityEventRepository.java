package com.fix.channel.repository;

import com.fix.channel.entity.SecurityEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
  List<SecurityEvent> findTop20ByMemberIdOrderByIdDesc(Long memberId);
}
