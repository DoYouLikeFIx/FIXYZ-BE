package com.fix.channel.repository;

import com.fix.channel.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
  Optional<Member> findByMemberNo(String memberNo);
}
