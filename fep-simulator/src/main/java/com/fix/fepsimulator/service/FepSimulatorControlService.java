package com.fix.fepsimulator.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.fepsimulator.entity.SimulatorConnection;
import com.fix.fepsimulator.entity.SimulatorMessage;
import com.fix.fepsimulator.entity.SimulatorRule;
import com.fix.fepsimulator.repository.SimulatorConnectionRepository;
import com.fix.fepsimulator.repository.SimulatorMessageRepository;
import com.fix.fepsimulator.repository.SimulatorRuleRepository;
import com.fix.fepsimulator.vo.SimulatorChaosCommand;
import com.fix.fepsimulator.vo.SimulatorChaosResult;
import com.fix.fepsimulator.vo.SimulatorRuleQueryCommand;
import com.fix.fepsimulator.vo.SimulatorRuleResult;
import com.fix.fepsimulator.vo.SimulatorRuleUpsertCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FepSimulatorControlService {

  private final SimulatorConnectionRepository simulatorConnectionRepository;
  private final SimulatorMessageRepository simulatorMessageRepository;
  private final SimulatorRuleRepository simulatorRuleRepository;

  @Transactional
  public SimulatorRuleResult upsertRule(SimulatorRuleUpsertCommand command) {
    SimulatorRule rule = simulatorRuleRepository.findByRuleCode(command.getRuleCode())
        .orElseGet(() -> SimulatorRule.create(command.getRuleCode(), command.getAction(), command.isEnabled()));

    rule.update(command.getAction(), command.isEnabled());
    simulatorRuleRepository.save(rule);

    return SimulatorRuleResult.of(
        rule.getRuleCode(),
        rule.getAction(),
        rule.isEnabled(),
        simulatorRuleRepository.existsEnabledRuleByRuleCode(rule.getRuleCode())
    );
  }

  @Transactional(readOnly = true)
  public SimulatorRuleResult getRule(SimulatorRuleQueryCommand command) {
    SimulatorRule rule = simulatorRuleRepository.findByRuleCode(command.getRuleCode())
        .orElseThrow(() -> new BusinessException(ErrorCode.SYS_RESOURCE_NOT_FOUND, "simulator rule not found"));

    return SimulatorRuleResult.of(
        rule.getRuleCode(),
        rule.getAction(),
        rule.isEnabled(),
        simulatorRuleRepository.existsEnabledRuleByRuleCode(rule.getRuleCode())
    );
  }

  @Transactional
  public SimulatorChaosResult runChaos(SimulatorChaosCommand command) {
    SimulatorConnection connection = simulatorConnectionRepository.findByConnectionKey(command.getConnectionKey())
        .orElseGet(() -> simulatorConnectionRepository.save(
            SimulatorConnection.connected(command.getConnectionKey(), "sim://" + command.getConnectionKey())
        ));

    connection.markStatus("CHAOS_ACTIVE");
    simulatorMessageRepository.save(SimulatorMessage.of(
        connection.getId(),
        "CHAOS",
        "OUTBOUND",
        command.getScenario() + "|intensity=" + command.getIntensity()
    ));

    return SimulatorChaosResult.of(connection.getConnectionKey(), command.getScenario(), "CHAOS_ACCEPTED");
  }
}
