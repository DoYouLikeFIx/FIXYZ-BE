package com.fix.channel.repository;

import com.fix.channel.entity.Notification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findByMemberId(Long memberId, Pageable pageable);

  List<Notification> findByMemberIdAndIdLessThan(Long memberId, Long cursorId, Pageable pageable);
}
