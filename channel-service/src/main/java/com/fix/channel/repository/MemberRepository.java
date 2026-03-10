package com.fix.channel.repository;

import com.fix.channel.entity.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {
  Optional<Member> findByMemberNo(String memberNo);

  Optional<Member> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.email = :email")
  Optional<Member> findByEmailForUpdate(@Param("email") String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.id = :memberId")
  Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

  boolean existsByEmail(String email);
}
