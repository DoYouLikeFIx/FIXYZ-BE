package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.corebank.domain.AccountStatus;
import com.fix.corebank.exception.order.PositionLockContentionException;
import com.fix.corebank.entity.Account;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepOutboundOrderPayload;
import com.fix.corebank.entity.Order;
import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.vo.AccountPositionQueryCommand;
import com.fix.corebank.vo.AccountPositionsQueryCommand;
import com.fix.corebank.vo.AccountPositionResult;
import com.fix.corebank.vo.AccountStatusQueryCommand;
import com.fix.corebank.vo.AccountStatusResult;
import com.fix.corebank.vo.AccountStatusTransitionCommand;
import com.fix.corebank.vo.AccountStatusTransitionResult;
import com.fix.corebank.vo.AccountSummaryQueryCommand;
import com.fix.corebank.vo.AccountOrderHistoryItemResult;
import com.fix.corebank.vo.AccountOrderHistoryQueryCommand;
import com.fix.corebank.vo.AccountOrderHistoryResult;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderRequeryCommand;
import com.fix.corebank.vo.InternalOrderResult;
import com.fix.corebank.vo.PortfolioQueryCommand;
import com.fix.corebank.vo.PortfolioResult;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CorebankOrderService {

  private final AccountRepository accountRepository;
  private final PositionRepository positionRepository;
  private final ExecutionRepository executionRepository;
  private final CorebankOrderPersistenceService orderPersistenceService;
  private final FepClient fepClient;
  private final PositionLockMetrics positionLockMetrics;

  @Value("${recovery.max-retry-count:5}")
  private int maxRetryCount = 5;

  @Value("${recovery.status-query.max-attempts:2}")
  private int statusQueryMaxAttempts = 2;

  @Value("${recovery.status-query.backoff-ms:0}")
  private long statusQueryBackoffMs = 0L;

  private Clock limitWindowClock = Clock.systemUTC();

  @Value("${corebank.order.limit-window-zone:UTC}")
  private String limitWindowZone = "UTC";

  @PostConstruct
  void validateRecoveryConfiguration() {
    if (statusQueryMaxAttempts < 1) {
      throw new IllegalStateException("recovery.status-query.max-attempts must be >= 1");
    }
    if (statusQueryBackoffMs < 0L) {
      throw new IllegalStateException("recovery.status-query.backoff-ms must be >= 0");
    }
  }

  @Transactional(readOnly = true)
  public PortfolioResult getPortfolio(PortfolioQueryCommand command) {
    Account account = accountRepository.findById(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    Position position = positionRepository.findByAccountIdAndSymbol(command.getAccountId(), command.getSymbol())
        .orElse(Position.of(command.getAccountId(), command.getSymbol(), BigDecimal.ZERO, BigDecimal.ZERO));

    BigDecimal todaySellQty = executionRepository.sumSellQuantityByAccountAndSymbolBetween(
        command.getAccountId(),
        command.getSymbol(),
        startOfLimitWindowDay(),
        startOfNextLimitWindowDay()
    );

    return PortfolioResult.of(
        account.getId(),
        account.getAccountNo(),
        command.getSymbol(),
        position.getQty(),
        account.getDailySellLimit(),
        todaySellQty
    );
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AccountPositionResult getAccountPosition(AccountPositionQueryCommand command) {
    Account account = getOwnedAccount(command.getAccountId(), command.getMemberId());

    Optional<Position> positionOptional = positionRepository.findByAccountIdAndSymbol(
        command.getAccountId(),
        command.getSymbol()
    );
    BigDecimal quantity = positionOptional.map(Position::getQty).orElse(BigDecimal.ZERO);
    BigDecimal availableQuantity = quantity;

    return AccountPositionResult.of(
        account.getId(),
        command.getMemberId(),
        command.getSymbol(),
        quantity,
        availableQuantity,
        account.getCashBalance(),
        account.getCurrency(),
        resolveAsOf(account, positionOptional)
    );
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<AccountPositionResult> getAccountPositions(AccountPositionsQueryCommand command) {
    Account account = accountRepository.findById(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    if (!account.getMemberId().equals(command.getMemberId())) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP, "forbidden account ownership");
    }

    return positionRepository.findAllByAccountIdAndQtyGreaterThanOrderBySymbolAsc(
            command.getAccountId(),
            BigDecimal.ZERO
        ).stream()
        .map(position -> AccountPositionResult.of(
            account.getId(),
            command.getMemberId(),
            position.getSymbol(),
            position.getQty(),
            position.getQty(),
            account.getCashBalance(),
            account.getCurrency(),
            resolveAsOf(account, Optional.of(position))
        ))
        .toList();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AccountPositionResult getAccountSummary(AccountSummaryQueryCommand command) {
    Account account = accountRepository.findById(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    if (!account.getMemberId().equals(command.getMemberId())) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP, "forbidden account ownership");
    }

    return AccountPositionResult.of(
        account.getId(),
        command.getMemberId(),
        "",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        account.getCashBalance(),
        account.getCurrency(),
        resolveAsOf(account, Optional.empty())
    );
  }

  @Transactional(readOnly = true)
  public AccountPositionResult getAccountSummary(AccountStatusQueryCommand command) {
    Account account = getOwnedAccount(command.getAccountId(), command.getMemberId());
    return AccountPositionResult.of(
        account.getId(),
        account.getMemberId(),
        "",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        account.getCashBalance(),
        account.getCurrency(),
        resolveAccountAsOf(account)
    );
  }

  @Transactional(readOnly = true)
  public List<AccountPositionResult> getAccountPositions(AccountStatusQueryCommand command) {
    Account account = getOwnedAccount(command.getAccountId(), command.getMemberId());
    return positionRepository.findAllByAccountIdOrderBySymbolAsc(command.getAccountId()).stream()
        .map(position -> AccountPositionResult.of(
            account.getId(),
            account.getMemberId(),
            position.getSymbol(),
            position.getQty(),
            position.getQty(),
            account.getCashBalance(),
            account.getCurrency(),
            resolveAsOf(account, Optional.of(position))
        ))
        .toList();
  }

  @Transactional(readOnly = true)
  public AccountStatusResult getAccountStatus(AccountStatusQueryCommand command) {
    return toAccountStatusResult(getOwnedAccount(command.getAccountId(), command.getMemberId()));
  }

  @Transactional(readOnly = true)
  public AccountStatusResult getDefaultAccountStatus(Long memberId) {
    Account account = accountRepository.findByMemberId(memberId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));
    return toAccountStatusResult(account);
  }

  @Transactional(readOnly = true)
  public AccountOrderHistoryResult getAccountOrderHistory(AccountOrderHistoryQueryCommand command) {
    Account account = accountRepository.findById(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    if (!account.getMemberId().equals(command.getMemberId())) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP, "forbidden account ownership");
    }

    CorebankOrderPersistenceService.AccountOrderHistoryPage historyPage =
        orderPersistenceService.findAccountOrderHistory(
            command.getAccountId(),
            command.getPage(),
            command.getSize()
        );
    List<AccountOrderHistoryItemResult> content = historyPage.content().stream()
        .map(this::mapOrderHistoryItem)
        .toList();

    return AccountOrderHistoryResult.of(
        content,
        historyPage.totalElements(),
        historyPage.totalPages(),
        historyPage.number(),
        historyPage.size()
    );
  }

  public AccountStatusTransitionResult transitionAccountStatus(AccountStatusTransitionCommand command) {
    return orderPersistenceService.transitionAccountStatus(command);
  }

  public InternalOrderResult createOrder(InternalOrderCreateCommand command) {
    return orderPersistenceService.findOrder(command.getClOrdId())
        .map(existing -> mapToOrderResult(existing, true))
        .orElseGet(() -> createFreshOrder(command));
  }

  public InternalOrderResult requeryOrder(InternalOrderRequeryCommand command) {
    CorebankOrderPersistenceService.OrderSnapshot order = orderPersistenceService.getRequiredOrder(command.getClOrdId());
    StatusQueryOutcome outcome = queryOrderStatusWithRetry(order);
    if (outcome instanceof StatusQuerySuccess success) {
      FepOrderResult gatewayStatus = success.gatewayStatus();
      CorebankOrderPersistenceService.OrderSnapshot currentOrder = findCurrentOrder(command.getClOrdId())
          .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "order not found"));
      String targetStatus = statusForRequery(currentOrder.status(), gatewayStatus.ordStatus());
      String requeryExternalSyncStatus = externalSyncStatusForRequery(
          targetStatus,
          gatewayStatus.ordStatus(),
          command.getAttemptCount()
      );
      String requeryMessage = failureReasonForRequery(
          currentOrder,
          targetStatus,
          gatewayStatus,
          requeryExternalSyncStatus
      );
      CorebankOrderPersistenceService.OrderStateUpdateResult updateResult =
          orderPersistenceService.updateOrderStateIfSnapshotMatches(
              currentOrder,
              targetStatus,
              requeryExternalSyncStatus,
              resolveFepReferenceId(currentOrder, gatewayStatus.fepOrderId()),
              requeryMessage
          );
      CorebankOrderPersistenceService.OrderSnapshot resolvedOrder = updateResult.order();
      if (resolvedOrder == null) {
        throw new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "order not found");
      }
      return mapSnapshotRequeryResult(
          resolvedOrder,
          requeryMessage,
          gatewayStatus.ordStatus(),
          command.getAttemptCount()
      );
    }
    StatusQueryFailure failure = (StatusQueryFailure) outcome;
    BusinessException ex = failure.failure();
    if (!isRetriableRequeryFailure(ex)) {
      throw ex;
    }
    CorebankOrderPersistenceService.OrderSnapshot currentOrder = failure.currentOrder();
    if (currentOrder == null) {
      throw ex;
    }
    if (!isTerminalOrderStatus(currentOrder.status())) {
      CorebankOrderPersistenceService.OrderStateUpdateResult updateResult =
          orderPersistenceService.updateOrderStateIfSnapshotMatches(
              currentOrder,
              currentOrder.status(),
              externalSyncStatusForRetriableFailure(command.getAttemptCount()),
              currentOrder.fepReferenceId(),
              failureReason(ex)
          );
      currentOrder = updateResult.order();
      if (currentOrder == null) {
        throw ex;
      }
    }
    return mapToRequeryResult(
        currentOrder,
        ex.getMessage(),
        classifyRetriableFailure(currentOrder.status(), command.getAttemptCount())
    );
  }

  private StatusQueryOutcome queryOrderStatusWithRetry(CorebankOrderPersistenceService.OrderSnapshot initialOrder) {
    int maxAttempts = statusQueryMaxAttempts;
    String correlationId = correlationId("requery", initialOrder.clOrdId());
    CorebankOrderPersistenceService.OrderSnapshot currentOrder = initialOrder;
    BusinessException firstRetriableFailure = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return new StatusQuerySuccess(fepClient.queryOrderStatus(initialOrder.clOrdId(), correlationId));
      } catch (BusinessException ex) {
        boolean retriableFailure = isRetriableRequeryFailure(ex);
        if (retriableFailure) {
          if (firstRetriableFailure == null) {
            firstRetriableFailure = ex;
          }
        }
        Optional<CorebankOrderPersistenceService.OrderSnapshot> refreshedOrder = findCurrentOrder(initialOrder.clOrdId());
        if (refreshedOrder.isEmpty()) {
          return new StatusQueryFailure(finalStatusQueryFailure(ex, firstRetriableFailure, retriableFailure, attempt, maxAttempts), null);
        }
        currentOrder = refreshedOrder.get();
        if (!shouldRetryStatusQuery(currentOrder.status(), ex, attempt, maxAttempts)) {
          return new StatusQueryFailure(
              finalStatusQueryFailure(ex, firstRetriableFailure, retriableFailure, attempt, maxAttempts),
              currentOrder
          );
        }
        applyStatusQueryBackoff();
      }
    }
    throw new IllegalStateException("status query retry exited without terminal outcome");
  }

  private InternalOrderResult createFreshOrder(InternalOrderCreateCommand command) {
    try {
      CorebankOrderPersistenceService.PendingOrderSubmission pendingOrder;
      try {
        pendingOrder = orderPersistenceService.prepareOrderSubmission(command);
      } catch (PositionLockContentionException ex) {
        throw concurrencyConflict(command, ex);
      }
      try {
        FepOrderResult gatewayOrder = fepClient.submitOrder(
            toFepPayload(pendingOrder),
            correlationId("submit", command.getClOrdId())
        );
        CorebankOrderPersistenceService.OrderSnapshot updatedOrder =
            orderPersistenceService.updateOrderState(
                pendingOrder.clOrdId(),
                statusForSubmitConfirmation(pendingOrder.status(), gatewayOrder.ordStatus()),
                externalSyncStatusForSubmitConfirmation(gatewayOrder.ordStatus()),
                gatewayOrder.fepOrderId(),
                failureReasonForSubmitConfirmation(gatewayOrder)
            );
        return mapToOrderResult(updatedOrder, false);
      } catch (BusinessException ex) {
        orderPersistenceService.updateOrderState(
            pendingOrder.clOrdId(),
            pendingOrder.status(),
            externalSyncStatusForSubmitFailure(ex),
            null,
            failureReason(ex)
        );
        throw ex;
      }
    } catch (DataIntegrityViolationException e) {
      return orderPersistenceService.findOrder(command.getClOrdId())
          .map(existing -> mapToOrderResult(existing, true))
          .orElseThrow(() -> e);
    }
  }

  private BusinessException concurrencyConflict(InternalOrderCreateCommand command, RuntimeException ex) {
    positionLockMetrics.incrementConflicts();
    return new BusinessException(
        ErrorCode.CORE_CONCURRENCY_CONFLICT,
        ErrorCode.CORE_CONCURRENCY_CONFLICT.defaultMessage(),
        ex,
        new ErrorMetadata("error.core.concurrency_conflict", "CONCURRENCY_FAILURE"),
        Map.of(
            "accountId", command.getAccountId(),
            "symbol", command.getSymbol(),
            "clOrdId", command.getClOrdId(),
            "failureReason", "POSITION_LOCK"
        )
    );
  }

  private InternalOrderResult mapToOrderResult(CorebankOrderPersistenceService.OrderSnapshot order, boolean idempotent) {
    return InternalOrderResult.execution(
        order.orderId(),
        order.clOrdId(),
        order.status(),
        order.externalSyncStatus(),
        idempotent,
        order.orderQty(),
        order.executionResult(),
        order.executedQty(),
        order.leavesQty(),
        order.executedPrice(),
        order.fepReferenceId(),
        order.executedAt()
    );
  }

  private InternalOrderResult mapToRequeryResult(
      CorebankOrderPersistenceService.OrderSnapshot order,
      String message,
      RequerySignal signal
  ) {
    return InternalOrderResult.requery(
        order.orderId(),
        order.clOrdId(),
        order.status(),
        order.externalSyncStatus(),
        true,
        order.orderQty(),
        order.executionResult(),
        order.executedQty(),
        order.leavesQty(),
        order.executedPrice(),
        order.fepReferenceId(),
        order.executedAt(),
        message,
        signal.retriable(),
        signal.escalationRequired(),
        signal.attemptCount(),
        signal.maxRetryCount()
    );
  }

  private RequerySignal classifyRequeryOutcome(FepOrdStatus ordStatus, int attemptCount) {
    boolean escalationRequired = isEscalationThresholdReached(attemptCount);
    return switch (ordStatus) {
      case UNKNOWN, PENDING, MALFORMED -> new RequerySignal(!escalationRequired, escalationRequired, attemptCount, maxRetryCount);
      case REJECTED, PARTIALLY_FILLED, CANCELED -> new RequerySignal(false, true, attemptCount, maxRetryCount);
      case FILLED -> new RequerySignal(false, false, attemptCount, maxRetryCount);
    };
  }

  private RequerySignal classifyRetriableFailure(String currentOrderStatus, int attemptCount) {
    if (isTerminalOrderStatus(currentOrderStatus)) {
      return new RequerySignal(false, false, attemptCount, maxRetryCount);
    }
    boolean escalationRequired = isEscalationThresholdReached(attemptCount);
    return new RequerySignal(!escalationRequired, escalationRequired, attemptCount, maxRetryCount);
  }

  private boolean isRetriableRequeryFailure(BusinessException ex) {
    return ex.getErrorCode() == ErrorCode.FEP_GATEWAY_TIMEOUT
        || ex.getErrorCode() == ErrorCode.FEP_GATEWAY_UNAVAILABLE;
  }

  private boolean shouldRetryStatusQuery(
      String currentOrderStatus,
      BusinessException ex,
      int attempt,
      int maxAttempts
  ) {
    return !isTerminalOrderStatus(currentOrderStatus)
        && isRetriableRequeryFailure(ex)
        && attempt < maxAttempts;
  }

  private InternalOrderResult mapSnapshotRequeryResult(
      CorebankOrderPersistenceService.OrderSnapshot currentOrder,
      String fallbackMessage,
      FepOrdStatus fallbackStatus,
      int attemptCount
  ) {
    return mapToRequeryResult(
        currentOrder,
        firstNonBlank(currentOrder.failureReason(), fallbackMessage),
        snapshotRequerySignal(currentOrder, fallbackStatus, attemptCount)
    );
  }

  private String externalSyncStatusForRequery(String targetStatus, FepOrdStatus ordStatus, int attemptCount) {
    return switch (ordStatus) {
      case FILLED -> FepOrdStatus.FILLED.name().equals(targetStatus)
          ? Order.EXTERNAL_SYNC_CONFIRMED
          : Order.EXTERNAL_SYNC_ESCALATED;
      case PARTIALLY_FILLED, CANCELED, REJECTED -> Order.EXTERNAL_SYNC_ESCALATED;
      case UNKNOWN, PENDING, MALFORMED -> externalSyncStatusForRetriableFailure(attemptCount);
    };
  }

  private String externalSyncStatusForRetriableFailure(int attemptCount) {
    return isEscalationThresholdReached(attemptCount)
        ? Order.EXTERNAL_SYNC_ESCALATED
        : Order.EXTERNAL_SYNC_FAILED;
  }

  private String statusForSubmitConfirmation(String currentStatus, FepOrdStatus ordStatus) {
    return ordStatus == FepOrdStatus.FILLED ? FepOrdStatus.FILLED.name() : currentStatus;
  }

  private String statusForRequery(String currentStatus, FepOrdStatus gatewayStatus) {
    if (isTerminalOrderStatus(currentStatus)) {
      return currentStatus;
    }
    return gatewayStatus == FepOrdStatus.FILLED ? FepOrdStatus.FILLED.name() : currentStatus;
  }

  private String externalSyncStatusForSubmitConfirmation(FepOrdStatus ordStatus) {
    return switch (ordStatus) {
      case FILLED -> Order.EXTERNAL_SYNC_CONFIRMED;
      case PENDING, UNKNOWN, MALFORMED -> Order.EXTERNAL_SYNC_FAILED;
      case PARTIALLY_FILLED, CANCELED, REJECTED -> Order.EXTERNAL_SYNC_ESCALATED;
    };
  }

  private String externalSyncStatusForSubmitFailure(BusinessException ex) {
    return isRetriableSubmitFailure(ex)
        ? Order.EXTERNAL_SYNC_FAILED
        : Order.EXTERNAL_SYNC_ESCALATED;
  }

  private boolean isRetriableSubmitFailure(BusinessException ex) {
    return ex.getErrorCode() == ErrorCode.FEP_GATEWAY_TIMEOUT
        || ex.getErrorCode() == ErrorCode.FEP_GATEWAY_UNAVAILABLE;
  }

  private boolean isEscalationThresholdReached(int attemptCount) {
    return attemptCount >= maxRetryCount;
  }

  private void applyStatusQueryBackoff() {
    if (statusQueryBackoffMs == 0L) {
      return;
    }
    try {
      Thread.sleep(statusQueryBackoffMs);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw new BusinessException(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "status query retry backoff interrupted",
          interruptedException
      );
    }
  }

  private Optional<CorebankOrderPersistenceService.OrderSnapshot> findCurrentOrder(String clOrdId) {
    return orderPersistenceService.findOrder(clOrdId);
  }

  private BusinessException finalStatusQueryFailure(
      BusinessException currentFailure,
      BusinessException firstRetriableFailure,
      boolean retriableFailure,
      int attempt,
      int maxAttempts
  ) {
    if (retriableFailure && attempt >= maxAttempts && firstRetriableFailure != null) {
      return firstRetriableFailure;
    }
    return currentFailure;
  }

  private RequerySignal snapshotRequerySignal(
      CorebankOrderPersistenceService.OrderSnapshot currentOrder,
      FepOrdStatus fallbackStatus,
      int attemptCount
  ) {
    if (Order.EXTERNAL_SYNC_CONFIRMED.equals(currentOrder.externalSyncStatus())) {
      return new RequerySignal(false, false, attemptCount, maxRetryCount);
    }
    if (Order.EXTERNAL_SYNC_ESCALATED.equals(currentOrder.externalSyncStatus())) {
      return new RequerySignal(false, true, attemptCount, maxRetryCount);
    }
    if (Order.EXTERNAL_SYNC_FAILED.equals(currentOrder.externalSyncStatus())) {
      return classifyRetriableFailure(currentOrder.status(), attemptCount);
    }
    return classifyRequeryOutcome(fallbackStatus, attemptCount);
  }

  private boolean isTerminalOrderStatus(String status) {
    return switch (status) {
      case "FILLED", "PARTIALLY_FILLED", "CANCELED", "REJECTED" -> true;
      default -> false;
    };
  }

  private FepOutboundOrderPayload toFepPayload(CorebankOrderPersistenceService.PendingOrderSubmission pendingOrder) {
    return new FepOutboundOrderPayload(
        pendingOrder.clOrdId(),
        pendingOrder.accountNo(),
        pendingOrder.symbol(),
        FepSecurityExchange.KRX,
        FepSide.valueOf(pendingOrder.side()),
        FepOrderType.LIMIT,
        pendingOrder.orderQty().longValueExact(),
        pendingOrder.orderPrice().longValueExact(),
        null,
        null,
        null,
        null,
        pendingOrder.currency(),
        pendingOrder.clOrdId()
    );
  }

  private String correlationId(String operation, String clOrdId) {
    return CorrelationIdSupport.currentOrGenerate();
  }

  private String resolveFepReferenceId(CorebankOrderPersistenceService.OrderSnapshot order, String gatewayFepOrderId) {
    return gatewayFepOrderId != null && !gatewayFepOrderId.isBlank()
        ? gatewayFepOrderId
        : order.fepReferenceId();
  }

  private String failureReasonForRequery(
      CorebankOrderPersistenceService.OrderSnapshot currentOrder,
      String targetStatus,
      FepOrderResult result,
      String externalSyncStatus
  ) {
    if (Order.EXTERNAL_SYNC_CONFIRMED.equals(externalSyncStatus)) {
      return null;
    }
    if (result.ordStatus() == FepOrdStatus.FILLED && !FepOrdStatus.FILLED.name().equals(targetStatus)) {
      return firstNonBlank(currentOrder.failureReason(), result.message());
    }
    return failureReasonForRequery(result);
  }

  private String failureReasonForRequery(FepOrderResult result) {
    return switch (result.ordStatus()) {
      case FILLED -> null;
      case PARTIALLY_FILLED, CANCELED -> firstNonBlank(result.message(), result.ordStatus().name());
      case REJECTED -> firstNonBlank(result.rejectReason(), result.message(), "REJECTED");
      case UNKNOWN, PENDING -> result.message();
      case MALFORMED -> firstNonBlank(result.parseError(), result.message());
    };
  }

  private String failureReasonForSubmitConfirmation(FepOrderResult result) {
    return switch (result.ordStatus()) {
      case FILLED -> null;
      case PENDING, UNKNOWN -> result.message();
      case MALFORMED -> firstNonBlank(result.parseError(), result.message(), "MALFORMED");
      case PARTIALLY_FILLED, CANCELED -> firstNonBlank(result.message(), result.ordStatus().name());
      case REJECTED -> firstNonBlank(result.rejectReason(), result.message(), "REJECTED");
    };
  }

  private String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }

  private String failureReason(BusinessException ex) {
    if (ex.getMetadata() != null && ex.getMetadata().operatorCode() != null && !ex.getMetadata().operatorCode().isBlank()) {
      return ex.getMetadata().operatorCode();
    }
    return ex.getErrorCode().code();
  }

  private Instant startOfLimitWindowDay() {
    ZoneId zoneId = resolveLimitWindowZone();
    return LocalDate.now(limitWindowClock.withZone(zoneId)).atStartOfDay(zoneId).toInstant();
  }

  private Instant startOfNextLimitWindowDay() {
    ZoneId zoneId = resolveLimitWindowZone();
    return LocalDate.now(limitWindowClock.withZone(zoneId)).plusDays(1).atStartOfDay(zoneId).toInstant();
  }

  private ZoneId resolveLimitWindowZone() {
    return ZoneId.of(limitWindowZone);
  }

  private Instant resolveAsOf(Account account, Optional<Position> positionOptional) {
    Instant accountUpdatedAt = account.getUpdatedAt();
    Instant positionUpdatedAt = positionOptional.map(Position::getUpdatedAt).orElse(null);

    if (accountUpdatedAt == null && positionUpdatedAt == null) {
      return Instant.now();
    }
    if (accountUpdatedAt == null) {
      return positionUpdatedAt;
    }
    if (positionUpdatedAt == null) {
      return accountUpdatedAt;
    }
    return accountUpdatedAt.isAfter(positionUpdatedAt) ? accountUpdatedAt : positionUpdatedAt;
  }

  private Instant resolveAccountAsOf(Account account) {
    Instant updatedAt = account.getUpdatedAt();
    return updatedAt != null ? updatedAt : Instant.now();
  }

  private Account getOwnedAccount(Long accountId, Long memberId) {
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    if (!account.getMemberId().equals(memberId)) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP, "forbidden account ownership");
    }
    return account;
  }

  private AccountStatusResult toAccountStatusResult(Account account) {
    AccountStatus accountStatus = parseAccountStatus(account);
    return AccountStatusResult.of(
        account.getId(),
        account.getMemberId(),
        account.getAccountNo(),
        accountStatus.name(),
        accountStatus.isOrderEligible(),
        accountStatus.isOrderEligible() ? null : ErrorCode.ORD_ACCOUNT_STATUS_BLOCKED.code(),
        resolveAccountAsOf(account)
    );
  }

  private AccountStatus parseAccountStatus(Account account) {
    try {
      return AccountStatus.from(account.getStatus());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "unsupported account status: " + account.getStatus(), ex);
    }
  }

  private AccountOrderHistoryItemResult mapOrderHistoryItem(
      CorebankOrderPersistenceService.AccountOrderHistoryRow row
  ) {
    BigDecimal qty = defaultDecimal(row.orderQty(), BigDecimal.ZERO);
    BigDecimal unitPrice = defaultDecimal(row.orderPrice(), BigDecimal.ZERO);
    return AccountOrderHistoryItemResult.of(
        row.symbol(),
        resolveSymbolName(row.symbol()),
        row.side(),
        qty,
        unitPrice,
        qty.multiply(unitPrice),
        row.status(),
        row.clOrdId(),
        row.createdAt()
    );
  }

  private BigDecimal defaultDecimal(BigDecimal value, BigDecimal fallback) {
    return value == null ? fallback : value;
  }

  private String resolveSymbolName(String symbol) {
    return switch (symbol) {
      case "005930" -> "삼성전자";
      case "000660" -> "SK하이닉스";
      case "035420" -> "NAVER";
      default -> symbol;
    };
  }

  private record RequerySignal(
      boolean retriable,
      boolean escalationRequired,
      int attemptCount,
      int maxRetryCount
  ) {
  }

  private sealed interface StatusQueryOutcome permits StatusQuerySuccess, StatusQueryFailure {
  }

  private record StatusQuerySuccess(FepOrderResult gatewayStatus) implements StatusQueryOutcome {
  }

  private record StatusQueryFailure(
      BusinessException failure,
      CorebankOrderPersistenceService.OrderSnapshot currentOrder
  ) implements StatusQueryOutcome {
  }
}
