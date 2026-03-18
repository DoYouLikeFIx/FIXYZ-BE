package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.entity.Execution;
import com.fix.corebank.entity.LedgerEntryRef;
import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import com.fix.corebank.entity.LedgerReconciliationCase;
import com.fix.corebank.entity.LedgerReconciliationCaseEvent;
import com.fix.corebank.entity.LedgerReconciliationCaseStatus;
import com.fix.corebank.entity.LedgerReconciliationRepair;
import com.fix.corebank.entity.LedgerReconciliationRepairOutcome;
import com.fix.corebank.entity.LedgerReconciliationRepairType;
import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.LedgerEntryRefRepository;
import com.fix.corebank.repository.LedgerIntegrityAnomalyRecordRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseEventRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseRepository;
import com.fix.corebank.repository.LedgerReconciliationRepairRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.vo.LedgerIntegrityCheckResult;
import com.fix.corebank.vo.LedgerReconciliationRepairCommand;
import com.fix.corebank.vo.LedgerReconciliationRepairResult;
import com.fix.corebank.vo.LedgerReconciliationRerunCommand;
import com.fix.corebank.vo.LedgerReconciliationRerunResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerRepairService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

  private final LedgerReconciliationCaseRepository caseRepository;
  private final LedgerReconciliationCaseEventRepository eventRepository;
  private final LedgerReconciliationRepairRepository repairRepository;
  private final LedgerEntryRefRepository ledgerEntryRefRepository;
  private final PositionRepository positionRepository;
  private final ExecutionRepository executionRepository;
  private final LedgerIntegrityService ledgerIntegrityService;
  private final LedgerIntegrityAnomalyRecordRepository anomalyRecordRepository;

  public LedgerRepairService(
      LedgerReconciliationCaseRepository caseRepository,
      LedgerReconciliationCaseEventRepository eventRepository,
      LedgerReconciliationRepairRepository repairRepository,
      LedgerEntryRefRepository ledgerEntryRefRepository,
      PositionRepository positionRepository,
      ExecutionRepository executionRepository,
      LedgerIntegrityService ledgerIntegrityService,
      LedgerIntegrityAnomalyRecordRepository anomalyRecordRepository
  ) {
    this.caseRepository = caseRepository;
    this.eventRepository = eventRepository;
    this.repairRepository = repairRepository;
    this.ledgerEntryRefRepository = ledgerEntryRefRepository;
    this.positionRepository = positionRepository;
    this.executionRepository = executionRepository;
    this.ledgerIntegrityService = ledgerIntegrityService;
    this.anomalyRecordRepository = anomalyRecordRepository;
  }

  @Transactional
  public LedgerReconciliationRepairResult applyRepair(LedgerReconciliationRepairCommand command) {
    validateActorAndReason(command.getActor(), command.getReason());
    validateCaseId(command.getCaseId());
    validateRepairKey(command.getRepairKey());
    LedgerReconciliationRepairType repairType = LedgerReconciliationRepairType.from(command.getRepairType());

    LedgerReconciliationRepair existing = repairRepository.findByCaseIdAndRepairKey(command.getCaseId(), command.getRepairKey())
        .orElse(null);
    if (existing != null) {
      validateReplayType(existing, repairType);
      return toRepairResult(existing, currentCaseStatus(existing.getCaseId()), true);
    }

    LedgerReconciliationCase reconciliationCase = caseRepository.findById(command.getCaseId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "reconciliation case not found"));
    if (reconciliationCase.isTerminal()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "cannot repair terminal reconciliation case");
    }

    RepairMutation mutation = switch (repairType) {
      case REBUILD_POSITION_FROM_EXECUTIONS -> rebuildPositionFromExecutions(reconciliationCase);
      case ATTACH_LEDGER_CL_ORD_REF -> attachLedgerReference(reconciliationCase);
      case MARK_FALSE_POSITIVE -> markFalsePositive(reconciliationCase);
    };

    try {
      LedgerReconciliationRepair savedRepair = repairRepository.saveAndFlush(
          LedgerReconciliationRepair.of(
              reconciliationCase.getId(),
              command.getRepairKey(),
              repairType,
              mutation.outcome(),
              mutation.mutated(),
              command.getReason(),
              command.getActor(),
              command.getContext(),
              command.getCorrelationId(),
              mutation.summaryMessage()
          )
      );
      return toRepairResult(savedRepair, reconciliationCase.getStatus().name(), false);
    } catch (DataIntegrityViolationException ex) {
      LedgerReconciliationRepair concurrent = repairRepository.findByCaseIdAndRepairKey(
              command.getCaseId(),
              command.getRepairKey()
          )
          .orElseThrow(() -> ex);
      validateReplayType(concurrent, repairType);
      return toRepairResult(concurrent, currentCaseStatus(concurrent.getCaseId()), true);
    }
  }

  @Transactional
  public LedgerReconciliationRerunResult rerunCase(LedgerReconciliationRerunCommand command) {
    validateActorAndReason(command.getActor(), command.getReason());
    validateCaseId(command.getCaseId());

    LedgerReconciliationCase reconciliationCase = caseRepository.findById(command.getCaseId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "reconciliation case not found"));
    if (reconciliationCase.getStatus() != LedgerReconciliationCaseStatus.REPAIR_PENDING) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "reconciliation rerun requires REPAIR_PENDING case"
      );
    }

    LedgerIntegrityCheckResult rerunCheck = ledgerIntegrityService.runCheckAndStore();
    Long rerunRunId = rerunCheck.getRunId();
    if (rerunRunId == null) {
      throw new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "integrity run not found");
    }
    boolean anomalyStillPresent = anomalyRecordRepository.findAllByRunId(rerunRunId).stream()
        .anyMatch(anomaly -> matchesCase(reconciliationCase, anomaly));

    LedgerReconciliationCaseStatus previousStatus = reconciliationCase.getStatus();
    LedgerReconciliationCaseStatus targetStatus = anomalyStillPresent
        ? LedgerReconciliationCaseStatus.REOPENED
        : LedgerReconciliationCaseStatus.RESOLVED;
    boolean changed = reconciliationCase.transitionTo(targetStatus, rerunCheck.getCheckedAt());
    LedgerReconciliationCase savedCase = caseRepository.saveAndFlush(reconciliationCase);
    LedgerReconciliationCaseEvent savedEvent = eventRepository.saveAndFlush(
        LedgerReconciliationCaseEvent.statusChanged(
            savedCase.getId(),
            previousStatus,
            savedCase.getStatus(),
            command.getReason(),
            command.getActor(),
            command.getContext(),
            command.getCorrelationId()
        )
    );

    repairRepository.findFirstByCaseIdOrderByIdDesc(savedCase.getId()).ifPresent(repair -> {
      repair.linkRerun(rerunRunId, savedCase.getStatus());
      repairRepository.saveAndFlush(repair);
    });

    return LedgerReconciliationRerunResult.of(
        savedCase.getId(),
        previousStatus.name(),
        savedCase.getStatus().name(),
        changed,
        savedEvent.getId(),
        rerunRunId,
        anomalyStillPresent,
        command.getReason(),
        command.getActor(),
        command.getContext(),
        savedEvent.getCreatedAt()
    );
  }

  private RepairMutation rebuildPositionFromExecutions(LedgerReconciliationCase reconciliationCase) {
    if (reconciliationCase.getAccountId() == null || reconciliationCase.getSymbol() == null) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "REBUILD_POSITION_FROM_EXECUTIONS requires accountId and symbol"
      );
    }

    List<Execution> executions = executionRepository.findAllByAccountIdAndSymbolOrderByExecutedAtAsc(
        reconciliationCase.getAccountId(),
        reconciliationCase.getSymbol()
    ).stream()
        .sorted(Comparator.comparing(Execution::getExecutedAt).thenComparing(Execution::getId))
        .toList();
    if (executions.isEmpty()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "cannot rebuild position without executions");
    }

    Position rebuilt = Position.of(reconciliationCase.getAccountId(), reconciliationCase.getSymbol(), ZERO, ZERO);
    for (Execution execution : executions) {
      if ("BUY".equalsIgnoreCase(execution.getSide())) {
        rebuilt.applyBuy(execution.getExecQty(), execution.getExecPrice());
      } else if ("SELL".equalsIgnoreCase(execution.getSide())) {
        rebuilt.applySell(execution.getExecQty());
      } else {
        throw new BusinessException(
            ErrorCode.CONTRACT_VALIDATION_FAILED,
            "unsupported execution side for position rebuild: " + execution.getSide()
        );
      }
    }

    Position position = positionRepository.findByAccountIdAndSymbolForUpdate(
            reconciliationCase.getAccountId(),
            reconciliationCase.getSymbol()
        )
        .orElse(null);
    if (position == null) {
      positionRepository.saveAndFlush(Position.of(
          reconciliationCase.getAccountId(),
          reconciliationCase.getSymbol(),
          rebuilt.getQty(),
          rebuilt.getAvgPrice()
      ));
    } else {
      position.rebuildTo(rebuilt.getQty(), rebuilt.getAvgPrice());
      positionRepository.saveAndFlush(position);
    }

    transitionCaseForRepair(reconciliationCase, LedgerReconciliationCaseStatus.REPAIR_PENDING);
    return new RepairMutation(
        true,
        LedgerReconciliationRepairOutcome.APPLIED,
        "rebuilt position from " + executions.size() + " execution(s)"
    );
  }

  private RepairMutation attachLedgerReference(LedgerReconciliationCase reconciliationCase) {
    if (reconciliationCase.getLedgerEntryId() == null || reconciliationCase.getClOrdId() == null) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "ATTACH_LEDGER_CL_ORD_REF requires ledgerEntryId and clOrdId"
      );
    }

    boolean exists = ledgerEntryRefRepository.existsByLedgerEntryIdAndRefTypeAndRefValue(
        reconciliationCase.getLedgerEntryId(),
        "CL_ORD_ID",
        reconciliationCase.getClOrdId()
    );
    if (!exists) {
      ledgerEntryRefRepository.saveAndFlush(LedgerEntryRef.of(
          reconciliationCase.getLedgerEntryId(),
          "CL_ORD_ID",
          reconciliationCase.getClOrdId()
      ));
    }

    transitionCaseForRepair(reconciliationCase, LedgerReconciliationCaseStatus.REPAIR_PENDING);
    return new RepairMutation(
        !exists,
        exists ? LedgerReconciliationRepairOutcome.NO_OP : LedgerReconciliationRepairOutcome.APPLIED,
        exists ? "CL_ORD_ID reference already present" : "attached CL_ORD_ID reference"
    );
  }

  private RepairMutation markFalsePositive(LedgerReconciliationCase reconciliationCase) {
    transitionCaseForRepair(reconciliationCase, LedgerReconciliationCaseStatus.WAIVED);
    return new RepairMutation(
        false,
        LedgerReconciliationRepairOutcome.NO_OP,
        "marked reconciliation case as false positive"
    );
  }

  private void transitionCaseForRepair(
      LedgerReconciliationCase reconciliationCase,
      LedgerReconciliationCaseStatus targetStatus
  ) {
    LedgerReconciliationCaseStatus previousStatus = reconciliationCase.getStatus();
    boolean changed = reconciliationCase.transitionTo(targetStatus, Instant.now());
    caseRepository.saveAndFlush(reconciliationCase);
    if (changed) {
      eventRepository.saveAndFlush(
          LedgerReconciliationCaseEvent.statusChanged(
              reconciliationCase.getId(),
              previousStatus,
              reconciliationCase.getStatus(),
              "automatic repair transition",
              "system",
              "repair",
              null
          )
      );
    }
  }

  private boolean matchesCase(LedgerReconciliationCase reconciliationCase, LedgerIntegrityAnomalyRecord anomaly) {
    if (!reconciliationCase.getAnomalyType().equals(anomaly.getType())) {
      return false;
    }
    if (!matchesNullable(reconciliationCase.getAccountId(), anomaly.getAccountId())) {
      return false;
    }
    if (!matchesNullable(reconciliationCase.getSymbol(), anomaly.getSymbol())) {
      return false;
    }
    if (!matchesNullable(reconciliationCase.getPositionId(), anomaly.getPositionId())) {
      return false;
    }
    if (!matchesNullable(reconciliationCase.getExecutionId(), anomaly.getExecutionId())) {
      return false;
    }
    if (!matchesNullable(reconciliationCase.getOrderId(), anomaly.getOrderId())) {
      return false;
    }
    if (!matchesNullable(reconciliationCase.getClOrdId(), anomaly.getClOrdId())) {
      return false;
    }
    if (!matchesNullable(reconciliationCase.getJournalEntryId(), anomaly.getJournalEntryId())) {
      return false;
    }
    return matchesNullable(reconciliationCase.getLedgerEntryId(), anomaly.getLedgerEntryId());
  }

  private boolean matchesNullable(Object expected, Object actual) {
    return expected == null || expected.equals(actual);
  }

  private void validateReplayType(
      LedgerReconciliationRepair existing,
      LedgerReconciliationRepairType requestedRepairType
  ) {
    if (!existing.getRepairType().equals(requestedRepairType)) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "requested repairType does not match existing repair for given caseId and repairKey"
      );
    }
  }

  private String currentCaseStatus(Long caseId) {
    return caseRepository.findById(caseId)
        .map(caseEntity -> caseEntity.getStatus().name())
        .orElse(null);
  }

  private LedgerReconciliationRepairResult toRepairResult(
      LedgerReconciliationRepair repair,
      String caseStatus,
      boolean idempotent
  ) {
    return LedgerReconciliationRepairResult.of(
        repair.getId(),
        repair.getCaseId(),
        repair.getRepairKey(),
        repair.getRepairType().name(),
        repair.getOutcome().name(),
        idempotent,
        repair.isMutated(),
        caseStatus,
        repair.getRerunRunId(),
        repair.getRerunCaseStatus(),
        repair.getSummaryMessage(),
        repair.getUpdatedAt()
    );
  }

  private void validateActorAndReason(String actor, String reason) {
    if (actor == null || actor.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "reconciliation actor is required");
    }
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "reconciliation reason is required");
    }
  }

  private void validateCaseId(Long caseId) {
    if (caseId == null || caseId <= 0) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "reconciliation caseId is required");
    }
  }

  private void validateRepairKey(String repairKey) {
    if (repairKey == null || repairKey.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "repairKey is required");
    }
  }

  private record RepairMutation(
      boolean mutated,
      LedgerReconciliationRepairOutcome outcome,
      String summaryMessage
  ) {
  }
}
