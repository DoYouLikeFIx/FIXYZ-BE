package com.fix.channel.repository;

import com.fix.channel.entity.OtpVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
  Optional<OtpVerification> findTopByMemberIdOrderByIdDesc(Long memberId);
}
