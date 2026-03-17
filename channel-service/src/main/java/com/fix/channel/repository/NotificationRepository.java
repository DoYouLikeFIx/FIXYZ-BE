package com.fix.channel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fix.channel.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findByMemberId(Long memberId, Pageable pageable);

  List<Notification> findByMemberIdAndIdLessThan(Long memberId, Long cursorId, Pageable pageable);

  Optional<Notification> findByIdAndMemberId(Long id, Long memberId);
}
