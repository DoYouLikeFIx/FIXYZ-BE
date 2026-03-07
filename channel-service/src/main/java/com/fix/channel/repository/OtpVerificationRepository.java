package com.fix.channel.repository;

import com.fix.channel.entity.OtpVerification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
  List<OtpVerification> findByMemberId(Long memberId, Pageable pageable);
}
