package com.fix.fepsimulator.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fix.fepsimulator.entity.SimulatorRule;

public interface SimulatorRuleRepository extends JpaRepository<SimulatorRule, Long> {
  List<SimulatorRule> findAllByExpiresAtAfterOrderByAppliedAtDesc(Instant now);

  long deleteByExpiresAtLessThanEqual(Instant now);
}
