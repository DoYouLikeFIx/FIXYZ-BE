package com.fix.channel.repository;

import com.fix.channel.entity.SecurityEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
  List<SecurityEvent> findByMemberId(Long memberId, Pageable pageable);

  List<SecurityEvent> findByMemberIdAndIdLessThan(Long memberId, Long cursorId, Pageable pageable);
}
