package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import com.fix.corebank.entity.LedgerReconciliationCase;
import com.fix.corebank.entity.LedgerReconciliationCaseEvent;
import com.fix.corebank.entity.LedgerReconciliationCaseStatus;
import com.fix.corebank.repository.LedgerIntegrityAnomalyRecordRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseEventRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseRepository;
import com.fix.corebank.vo.LedgerReconciliationCaseCreateCommand;
import com.fix.corebank.vo.LedgerReconciliationCaseResult;
import com.fix.corebank.vo.LedgerReconciliationCaseTransitionCommand;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerReconciliationService {

  private static final Set<LedgerReconciliationCaseStatus> UNRESOLVED_STATUSES = EnumSet.of(
      LedgerReconciliationCaseStatus.NEW,
      LedgerReconciliationCaseStatus.ACKNOWLEDGED,
      LedgerReconciliationCaseStatus.REPAIR_PENDING,
      LedgerReconciliationCaseStatus.REOPENED
  );

  private final LedgerIntegrityAnomalyRecordRepository anomalyRecordRepository;
  private final LedgerReconciliationCaseRepository caseRepository;
  private final LedgerReconciliationCaseEventRepository eventRepository;

  public LedgerReconciliationService(
      LedgerIntegrityAnomalyRecordRepository anomalyRecordRepository,
      LedgerReconciliationCaseRepository caseRepository,
      LedgerReconciliationCaseEventRepository eventRepository
  ) {
    this.anomalyRecordRepository = anomalyRecordRepository;
    this.caseRepository = caseRepository;
    this.eventRepository = eventRepository;
  }

  @Transactional
  public LedgerReconciliationCaseResult createCase(LedgerReconciliationCaseCreateCommand command) {
    validateActorAndReason(command.getActor(), command.getReason());
    validateAnomalyId(command.getAnomalyId());

    LedgerReconciliationCase existing = caseRepository.findFirstByAnomalyIdAndStatusInOrderByIdDesc(
            command.getAnomalyId(),
            UNRESOLVED_STATUSES
        )
        .orElse(null);
    if (existing != null) {
      return toResult(existing, existing.getStatus().name(), false, false, null, existing.getLastTransitionAt());
    }

    LedgerIntegrityAnomalyRecord anomaly = anomalyRecordRepository.findById(command.getAnomalyId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "integrity anomaly not found"));

    Instant openedAt = Instant.now();
    LedgerReconciliationCase savedCase = caseRepository.saveAndFlush(
        LedgerReconciliationCase.openFromAnomaly(anomaly, openedAt)
    );
    LedgerReconciliationCaseEvent savedEvent = eventRepository.saveAndFlush(
        LedgerReconciliationCaseEvent.created(
            savedCase.getId(),
            command.getReason(),
            command.getActor(),
            command.getContext(),
            command.getCorrelationId()
        )
    );

    return toResult(savedCase, null, true, true, savedEvent.getId(), savedEvent.getCreatedAt());
  }

  @Transactional
  public LedgerReconciliationCaseResult transitionCase(LedgerReconciliationCaseTransitionCommand command) {
    validateActorAndReason(command.getActor(), command.getReason());
    if (command.getCaseId() == null || command.getCaseId() <= 0) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "reconciliation caseId is required");
    }

    LedgerReconciliationCase reconciliationCase = caseRepository.findById(command.getCaseId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "reconciliation case not found"));

    LedgerReconciliationCaseStatus previousStatus = reconciliationCase.getStatus();
    LedgerReconciliationCaseStatus targetStatus = LedgerReconciliationCaseStatus.from(command.getTargetStatus());
    Instant transitionedAt = Instant.now();
    boolean changed = reconciliationCase.transitionTo(targetStatus, transitionedAt);
    if (!changed) {
      return toResult(
          reconciliationCase,
          previousStatus.name(),
          false,
          false,
          null,
          reconciliationCase.getLastTransitionAt()
      );
    }

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

    return toResult(savedCase, previousStatus.name(), true, false, savedEvent.getId(), savedEvent.getCreatedAt());
  }

  private LedgerReconciliationCaseResult toResult(
      LedgerReconciliationCase reconciliationCase,
      String previousStatus,
      boolean changed,
      boolean created,
      Long eventId,
      Instant asOf
  ) {
    return LedgerReconciliationCaseResult.of(
        reconciliationCase.getId(),
        reconciliationCase.getAnomalyId(),
        reconciliationCase.getRunId(),
        previousStatus,
        reconciliationCase.getStatus().name(),
        changed,
        created,
        eventId,
        reconciliationCase.getAnomalyType(),
        reconciliationCase.getSummaryMessage(),
        reconciliationCase.getAccountId(),
        reconciliationCase.getSymbol(),
        reconciliationCase.getPositionId(),
        reconciliationCase.getExecutionId(),
        reconciliationCase.getOrderId(),
        reconciliationCase.getClOrdId(),
        reconciliationCase.getJournalEntryId(),
        reconciliationCase.getLedgerEntryId(),
        asOf
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

  private void validateAnomalyId(Long anomalyId) {
    if (anomalyId == null || anomalyId <= 0) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "reconciliation anomalyId is required");
    }
  }
}
