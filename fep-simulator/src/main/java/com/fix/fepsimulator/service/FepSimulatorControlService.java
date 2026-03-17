package com.fix.fepsimulator.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fix.common.web.CorrelationIdSupport;
import com.fix.fepsimulator.entity.SimulatorRule;
import com.fix.fepsimulator.repository.SimulatorRuleRepository;
import com.fix.fepsimulator.vo.ChaosRuleAction;
import com.fix.fepsimulator.vo.SimulatorRuleResult;
import com.fix.fepsimulator.vo.SimulatorRuleUpsertCommand;

@Service
@Transactional
public class FepSimulatorControlService {

  private static final Logger log = LoggerFactory.getLogger(FepSimulatorControlService.class);

  private final SimulatorRuleRepository simulatorRuleRepository;

  public FepSimulatorControlService(SimulatorRuleRepository simulatorRuleRepository) {
    this.simulatorRuleRepository = simulatorRuleRepository;
  }

  public SimulatorRuleResult applyRule(SimulatorRuleUpsertCommand command, String requestUri, String requestSource) {
    Instant now = Instant.now();
    cleanupExpired(now);

    SimulatorRule savedRule = simulatorRuleRepository.save(SimulatorRule.create(
        command.getAction().name(),
        command.getTargetSymbol(),
        command.getTargetExchange(),
        command.getTtlSeconds(),
        command.getMatchAmount(),
        command.getProbability(),
        now
    ));

    SimulatorRuleResult result = toResult(savedRule);
    logRuleMutation("APPLY_RULE", requestUri, requestSource, result);
    return result;
  }

  public int clearRules(String requestUri, String requestSource) {
    cleanupExpired(Instant.now());
    int clearedCount = Math.toIntExact(simulatorRuleRepository.count());
    simulatorRuleRepository.deleteAllInBatch();

    log.info(
        "operation=RESET_RULES correlationId={} requestUri={} requestSource={} clearedCount={}",
        CorrelationIdSupport.currentOrGenerate(),
        requestUri,
        requestSource,
        clearedCount
    );

    return clearedCount;
  }

  public Optional<ChaosRuleAction> resolveMatchingAction(String symbol, String exchange, Long amount) {
    List<SimulatorRuleResult> activeRules = listActiveRules();
    return activeRules.stream()
        .filter(rule -> matches(rule, symbol, exchange, amount))
        .filter(rule -> ThreadLocalRandom.current().nextDouble() <= rule.getProbability())
        .map(rule -> ChaosRuleAction.valueOf(rule.getAction()))
        .findFirst();
  }

  public List<SimulatorRuleResult> listActiveRules() {
    Instant now = Instant.now();
    cleanupExpired(now);
    return simulatorRuleRepository.findAllByExpiresAtAfterOrderByAppliedAtDesc(now).stream()
        .map(this::toResult)
        .toList();
  }

  private boolean matches(SimulatorRuleResult rule, String symbol, String exchange, Long amount) {
    if (exchange == null || !exchange.equalsIgnoreCase(rule.getTargetExchange())) {
      return false;
    }
    if (rule.getTargetSymbol() != null && (symbol == null || !rule.getTargetSymbol().equalsIgnoreCase(symbol))) {
      return false;
    }
    if (rule.getMatchAmount() != null && !rule.getMatchAmount().equals(amount)) {
      return false;
    }
    return true;
  }

  private void cleanupExpired(Instant now) {
    simulatorRuleRepository.deleteByExpiresAtLessThanEqual(now);
  }

  private SimulatorRuleResult toResult(SimulatorRule rule) {
    return SimulatorRuleResult.of(
        rule.getRuleId(),
        rule.getAction(),
        rule.getTargetSymbol(),
        rule.getTargetExchange(),
        rule.getMatchAmount(),
        rule.getProbability(),
        rule.getAppliedAt(),
        rule.getExpiresAt()
    );
  }

  private void logRuleMutation(String operation, String requestUri, String requestSource, SimulatorRuleResult rule) {
    log.info(
        "operation={} correlationId={} requestUri={} requestSource={} ruleId={} action={} targetSymbol={} targetExchange={} "
            + "matchAmount={} probability={} appliedAt={} expiresAt={}",
        operation,
        CorrelationIdSupport.currentOrGenerate(),
        requestUri,
        requestSource,
        rule.getRuleId(),
        rule.getAction(),
        rule.getTargetSymbol(),
        rule.getTargetExchange(),
        rule.getMatchAmount(),
        rule.getProbability(),
        rule.getAppliedAt(),
        rule.getExpiresAt()
    );
  }
}
