package com.fix.fepsimulator.repository.custom;

import com.fix.fepsimulator.entity.SimulatorRule;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class SimulatorRuleCustomRepositoryImpl implements SimulatorRuleCustomRepository {

  private final EntityManager entityManager;

  public SimulatorRuleCustomRepositoryImpl(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public boolean existsEnabledRuleByRuleCode(String ruleCode) {
    Long count = entityManager.createQuery(
            "select count(r) from SimulatorRule r where r.ruleCode = :ruleCode and r.enabled = true",
            Long.class
        )
        .setParameter("ruleCode", ruleCode)
        .getSingleResult();
    return count != null && count > 0;
  }
}
