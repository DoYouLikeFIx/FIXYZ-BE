package com.fix.fepsimulator.repository;

import com.fix.fepsimulator.entity.SimulatorRule;
import com.fix.fepsimulator.repository.custom.SimulatorRuleCustomRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorRuleRepository extends JpaRepository<SimulatorRule, Long>, SimulatorRuleCustomRepository {
  Optional<SimulatorRule> findByRuleCode(String ruleCode);
}
