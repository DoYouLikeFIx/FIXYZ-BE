package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.common.valuation.ValuationStatus;
import com.fix.common.valuation.ValuationUnavailableReason;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.corebank.client.FepQuoteSnapshotClient;
import com.fix.corebank.client.FepQuoteSnapshotResult;
import com.fix.corebank.config.CorebankMarketDataProperties;
import com.fix.corebank.domain.AccountStatus;
import com.fix.corebank.exception.order.PositionLockContentionException;
import com.fix.corebank.entity.Account;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepOutboundOrderPayload;
import com.fix.corebank.entity.Execution;
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
import com.fix.corebank.vo.InternalOrderSnapshotResult;
import com.fix.corebank.vo.InternalOrderRequeryCommand;
import com.fix.corebank.vo.InternalOrderResult;
import com.fix.corebank.vo.PortfolioQueryCommand;
import com.fix.corebank.vo.PortfolioResult;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  private static final int DECIMAL_SCALE = 4;

  private final AccountRepository accountRepository;
  private final PositionRepository positionRepository;
  private final ExecutionRepository executionRepository;
  private final CorebankOrderPersistenceService orderPersistenceService;
  private final FepClient fepClient;
  private final FepQuoteSnapshotClient fepQuoteSnapshotClient;
  private final CorebankAccountPositionQueryService accountPositionQueryService;
  private final QuoteFreshnessPolicy quoteFreshnessPolicy;
  private final CorebankMarketDataProperties corebankMarketDataProperties;
  private final PositionLockMetrics positionLockMetrics;

  @Value("${recovery.max-retry-count:5}")
  private int maxRetryCount = 5;

  @Value("${recovery.status-query.max-attempts:2}")
  private int statusQueryMaxAttempts = 2;

  @Value("${recovery.status-query.backoff-ms:0}")
  private long statusQueryBackoffMs = 0L;

  @Value("${corebank.order.preparation-retry.max-attempts:1}")
  private int orderPreparationRetryMaxAttempts = 1;

  @Value("${corebank.order.preparation-retry.backoff-ms:10}")
  private long orderPreparationRetryBackoffMs = 10L;

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
    if (orderPreparationRetryMaxAttempts < 1) {
      throw new IllegalStateException("corebank.order.preparation-retry.max-attempts must be >= 1");
    }
    if (orderPreparationRetryBackoffMs < 0L) {
      throw new IllegalStateException("corebank.order.preparation-retry.backoff-ms must be >= 0");
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
    CorebankAccountPositionQueryService.AccountPositionSnapshot snapshot =
        accountPositionQueryService.getOwnedAccountPosition(command);
    return toInquiryAccountPositionResult(snapshot.account(), command.getMemberId(), command.getSymbol(), snapshot.position());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<AccountPositionResult> getAccountPositions(AccountPositionsQueryCommand command) {
    CorebankAccountPositionQueryService.AccountPositionsSnapshot snapshot =
        accountPositionQueryService.getOwnedPositiveAccountPositions(command);
    Map<String, QuoteValuation> quoteValuations = loadInquiryQuoteValuations(
        snapshot.positions().stream()
            .map(Position::getSymbol)
            .toList()
    );
    Map<String, BigDecimal> realizedPnlDailyBySymbol = loadInquiryRealizedPnlDailyBySymbol(
        snapshot.account().getId(),
        snapshot.positions(),
        quoteValuations
    );

    return snapshot.positions().stream()
        .map(position -> toInquiryAccountPositionResult(
            snapshot.account(),
            command.getMemberId(),
            position,
            quoteValuations.getOrDefault(position.getSymbol(), QuoteValuation.unavailable(ValuationUnavailableReason.QUOTE_MISSING)),
            realizedPnlDailyBySymbol.get(position.getSymbol())
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

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<AccountPositionResult> getAccountPositions(AccountStatusQueryCommand command) {
    CorebankAccountPositionQueryService.AccountPositionsSnapshot snapshot =
        accountPositionQueryService.getOwnedAccountPositions(command);
    Map<String, QuoteValuation> quoteValuations = loadInquiryQuoteValuations(
        snapshot.positions().stream()
            .map(Position::getSymbol)
            .toList()
    );
    Map<String, BigDecimal> realizedPnlDailyBySymbol = loadInquiryRealizedPnlDailyBySymbol(
        snapshot.account().getId(),
        snapshot.positions(),
        quoteValuations
    );

    return snapshot.positions().stream()
        .map(position -> toInquiryAccountPositionResult(
            snapshot.account(),
            snapshot.account().getMemberId(),
            position,
            quoteValuations.getOrDefault(position.getSymbol(), QuoteValuation.unavailable(ValuationUnavailableReason.QUOTE_MISSING)),
            realizedPnlDailyBySymbol.get(position.getSymbol())
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
        .map(existing -> resolveIdempotentReplay(existing, command))
        .orElseGet(() -> createFreshOrder(command));
  }

  public InternalOrderSnapshotResult getOrderSnapshot(String clOrdId) {
    CorebankOrderPersistenceService.OrderSnapshot order = orderPersistenceService.getRequiredOrder(clOrdId);
    return InternalOrderSnapshotResult.of(
        order.orderId(),
        order.accountId(),
        order.clOrdId(),
        order.status(),
        order.externalSyncStatus(),
        order.fepReferenceId()
    );
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
      validateFreshMarketQuote(command);
      CorebankOrderPersistenceService.PendingOrderSubmission pendingOrder =
          prepareOrderSubmissionWithRetry(command);
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
          .map(existing -> resolveIdempotentReplay(existing, command))
          .orElseThrow(() -> e);
    }
  }

  private CorebankOrderPersistenceService.PendingOrderSubmission prepareOrderSubmissionWithRetry(
      InternalOrderCreateCommand command
  ) {
    RuntimeException lastFailure = null;
    for (int attempt = 1; attempt <= orderPreparationRetryMaxAttempts; attempt++) {
      try {
        return orderPersistenceService.prepareOrderSubmission(command);
      } catch (RuntimeException ex) {
        if (!isRetriableOrderPreparationFailure(ex)) {
          throw ex;
        }
        lastFailure = ex;
        if (attempt == orderPreparationRetryMaxAttempts) {
          throw concurrencyConflict(command, ex);
        }
        applyOrderPreparationRetryBackoff();
      }
    }
    throw concurrencyConflict(
        command,
        lastFailure != null ? lastFailure : new IllegalStateException("order preparation retry exhausted")
    );
  }

  private boolean isRetriableOrderPreparationFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof PositionLockContentionException
          || current instanceof jakarta.persistence.LockTimeoutException
          || current instanceof jakarta.persistence.PessimisticLockException
          || isTransientLockExceptionClassName(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean isTransientLockExceptionClassName(Throwable throwable) {
    String className = throwable.getClass().getName();
    return "org.hibernate.exception.LockAcquisitionException".equals(className)
        || "org.hibernate.PessimisticLockException".equals(className)
        || "org.springframework.dao.DeadlockLoserDataAccessException".equals(className)
        || "java.sql.SQLTransactionRollbackException".equals(className)
        || "com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException".equals(className);
  }

  private void applyOrderPreparationRetryBackoff() {
    if (orderPreparationRetryBackoffMs == 0L) {
      return;
    }
    try {
      Thread.sleep(orderPreparationRetryBackoffMs);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw new BusinessException(
          ErrorCode.CORE_CONCURRENCY_CONFLICT,
          "order preparation retry backoff interrupted",
          interruptedException
      );
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

  private InternalOrderResult resolveIdempotentReplay(
      CorebankOrderPersistenceService.OrderSnapshot existingOrder,
      InternalOrderCreateCommand command
  ) {
    validateReplayOwnership(existingOrder, command);
    validateReplayPayload(existingOrder, command);
    return mapToOrderResult(existingOrder, true);
  }

  private void validateReplayOwnership(
      CorebankOrderPersistenceService.OrderSnapshot existingOrder,
      InternalOrderCreateCommand command
  ) {
    if (!existingOrder.accountId().equals(command.getAccountId())) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP, "forbidden account ownership");
    }
  }

  private void validateReplayPayload(
      CorebankOrderPersistenceService.OrderSnapshot existingOrder,
      InternalOrderCreateCommand command
  ) {
    if (!existingOrder.symbol().equals(command.getSymbol())
        || !existingOrder.side().equals(normalizeSide(command.getSide()))
        || !normalizeOrderType(existingOrder.orderType()).equals(normalizeOrderType(command.getOrderType()))
        || compareNumeric(existingOrder.orderQty(), command.getQuantity()) != 0
        || compareNumeric(existingOrder.orderPrice(), command.getPrice()) != 0
        || compareNumeric(existingOrder.preTradePrice(), command.getPreTradePrice()) != 0
        || !Objects.equals(existingOrder.quoteSnapshotId(), command.getQuoteSnapshotId())
        || !Objects.equals(existingOrder.quoteAsOf(), command.getQuoteAsOf())
        || !Objects.equals(existingOrder.quoteSourceMode(), command.getQuoteSourceMode())) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "clOrdId replay payload mismatch");
    }
  }

  private int compareNumeric(BigDecimal left, BigDecimal right) {
    if (left == null && right == null) {
      return 0;
    }
    if (left == null || right == null) {
      return -1;
    }
    return left.compareTo(right);
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
        FepOrderType.valueOf(pendingOrder.orderType()),
        pendingOrder.orderQty().longValueExact(),
        pendingOrder.orderPrice() == null ? null : pendingOrder.orderPrice().longValueExact(),
        pendingOrder.quoteSnapshotId(),
        pendingOrder.quoteAsOf(),
        pendingOrder.quoteSourceMode(),
        pendingOrder.preTradePrice() == null ? null : pendingOrder.preTradePrice().longValueExact(),
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

  private String normalizeSide(String side) {
    if (side == null) {
      return null;
    }
    return side.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private String normalizeOrderType(String orderType) {
    if (orderType == null || orderType.isBlank()) {
      return FepOrderType.LIMIT.name();
    }
    String normalized = orderType.trim().toUpperCase(java.util.Locale.ROOT);
    if (!FepOrderType.LIMIT.name().equals(normalized) && !FepOrderType.MARKET.name().equals(normalized)) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "orderType must be LIMIT or MARKET");
    }
    return normalized;
  }

  private String failureReason(BusinessException ex) {
    if (ex.getMetadata() != null && ex.getMetadata().operatorCode() != null && !ex.getMetadata().operatorCode().isBlank()) {
      return ex.getMetadata().operatorCode();
    }
    return ex.getErrorCode().code();
  }

  private QuoteValuation loadFreshQuoteValuation(String symbol) {
    try {
      return toFreshQuoteValuation(symbol, queryLatestQuoteSnapshotRaw(symbol));
    } catch (BusinessException ex) {
      throw translateQuoteSnapshotFailure(symbol, ex);
    }
  }

  private Map<String, QuoteValuation> loadFreshQuoteValuations(List<String> symbols) {
    if (symbols.isEmpty()) {
      return Map.of();
    }

    Map<String, FepQuoteSnapshotResult> snapshots;
    try {
      snapshots = queryLatestQuoteSnapshotsRaw(symbols);
    } catch (BusinessException ex) {
      if (ex.getErrorCode() == ErrorCode.NOT_FOUND && !symbols.isEmpty()) {
        throw missingQuoteSnapshot(symbols.get(0));
      }
      throw ex;
    }
    Map<String, QuoteValuation> quoteValuations = new java.util.LinkedHashMap<>();
    for (String symbol : symbols) {
      FepQuoteSnapshotResult snapshot = snapshots.get(symbol);
      if (snapshot == null) {
        throw missingQuoteSnapshot(symbol);
      }
      quoteValuations.put(symbol, toFreshQuoteValuation(symbol, snapshot));
    }
    return quoteValuations;
  }

  private QuoteValuation toFreshQuoteValuation(String symbol, FepQuoteSnapshotResult snapshot) {
    QuoteFreshnessDecision decision = quoteFreshnessPolicy.evaluate(snapshot.quoteAsOf());
    if (decision.stale()) {
      throw staleQuote(symbol, snapshot, decision.snapshotAgeMs());
    }
    return QuoteValuation.fresh(
        resolveMarketPrice(snapshot),
        snapshot.quoteSnapshotId(),
        snapshot.quoteAsOf(),
        snapshot.quoteSourceMode()
    );
  }

  private QuoteValuation loadInquiryQuoteValuation(String symbol) {
    try {
      return toInquiryQuoteValuation(symbol, queryLatestQuoteSnapshotRaw(symbol));
    } catch (BusinessException ex) {
      return unavailableQuoteValuation(ex);
    }
  }

  private Map<String, QuoteValuation> loadInquiryQuoteValuations(List<String> symbols) {
    if (symbols.isEmpty()) {
      return Map.of();
    }

    Map<String, FepQuoteSnapshotResult> snapshots;
    try {
      snapshots = queryLatestQuoteSnapshotsRaw(symbols);
    } catch (BusinessException ex) {
      QuoteValuation unavailableQuoteValuation = unavailableQuoteValuation(ex);
      java.util.LinkedHashMap<String, QuoteValuation> unavailable = new java.util.LinkedHashMap<>();
      for (String symbol : symbols) {
        unavailable.put(symbol, unavailableQuoteValuation);
      }
      return unavailable;
    }

    java.util.LinkedHashMap<String, QuoteValuation> quoteValuations = new java.util.LinkedHashMap<>();
    for (String symbol : symbols) {
      FepQuoteSnapshotResult snapshot = snapshots.get(symbol);
      if (snapshot == null) {
        quoteValuations.put(symbol, QuoteValuation.unavailable(ValuationUnavailableReason.QUOTE_MISSING));
      } else {
        quoteValuations.put(symbol, toInquiryQuoteValuation(symbol, snapshot));
      }
    }
    return quoteValuations;
  }

  private QuoteValuation toInquiryQuoteValuation(String symbol, FepQuoteSnapshotResult snapshot) {
    QuoteFreshnessDecision decision = quoteFreshnessPolicy.evaluate(snapshot.quoteAsOf());
    if (decision.stale()) {
      return QuoteValuation.stale(snapshot.quoteSnapshotId(), snapshot.quoteAsOf(), snapshot.quoteSourceMode());
    }
    return QuoteValuation.fresh(
        resolveMarketPrice(snapshot),
        snapshot.quoteSnapshotId(),
        snapshot.quoteAsOf(),
        snapshot.quoteSourceMode()
    );
  }

  private QuoteValuation unavailableQuoteValuation(BusinessException ex) {
    if (ex.getErrorCode() == ErrorCode.NOT_FOUND) {
      return QuoteValuation.unavailable(ValuationUnavailableReason.QUOTE_MISSING);
    }
    if (ex.getErrorCode() == ErrorCode.FEP_GATEWAY_UNAVAILABLE || ex.getErrorCode() == ErrorCode.FEP_GATEWAY_TIMEOUT) {
      return QuoteValuation.unavailable(ValuationUnavailableReason.PROVIDER_UNAVAILABLE);
    }
    throw ex;
  }

  private FepQuoteSnapshotResult queryLatestQuoteSnapshotRaw(String symbol) {
    try {
      return fepQuoteSnapshotClient.queryLatestQuoteSnapshot(
          symbol,
          corebankMarketDataProperties.getQuoteSourceMode(),
          correlationId("quote", symbol)
      );
    } catch (BusinessException ex) {
      throw ex;
    }
  }

  private Map<String, FepQuoteSnapshotResult> queryLatestQuoteSnapshotsRaw(List<String> symbols) {
    try {
      return fepQuoteSnapshotClient.queryLatestQuoteSnapshots(
          symbols,
          corebankMarketDataProperties.getQuoteSourceMode(),
          correlationId("quote-batch", String.join(",", symbols))
      );
    } catch (BusinessException ex) {
      throw ex;
    }
  }

  private void validateFreshMarketQuote(InternalOrderCreateCommand command) {
    if (!FepOrderType.MARKET.name().equals(normalizeOrderType(command.getOrderType()))) {
      return;
    }
    if (command.getQuoteSnapshotId() == null || command.getQuoteSnapshotId().isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "quoteSnapshotId is required for MARKET orders");
    }
    if (command.getQuoteAsOf() == null) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "quoteAsOf is required for MARKET orders");
    }
    if (command.getQuoteSourceMode() == null) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "quoteSourceMode is required for MARKET orders");
    }
    QuoteFreshnessDecision decision = quoteFreshnessPolicy.evaluate(command.getQuoteAsOf());
    if (!decision.stale()) {
      return;
    }
    java.util.LinkedHashMap<String, Object> details = new java.util.LinkedHashMap<>();
    details.put("symbol", command.getSymbol());
    details.put("snapshotAgeMs", decision.snapshotAgeMs());
    if (command.getQuoteSnapshotId() != null) {
      details.put("quoteSnapshotId", command.getQuoteSnapshotId());
    }
    details.put("quoteAsOf", command.getQuoteAsOf().toString());
    if (command.getQuoteSourceMode() != null) {
      details.put("quoteSourceMode", command.getQuoteSourceMode().name());
    }
    throw new BusinessException(
        ErrorCode.STALE_QUOTE,
        ErrorCode.STALE_QUOTE.defaultMessage(),
        new ErrorMetadata("error.quote.stale", "STALE_QUOTE"),
        details
    );
  }

  private BusinessException translateQuoteSnapshotFailure(String symbol, BusinessException ex) {
    if (ex.getErrorCode() == ErrorCode.NOT_FOUND) {
      return missingQuoteSnapshot(symbol);
    }
    return ex;
  }

  private BusinessException missingQuoteSnapshot(String symbol) {
    return new BusinessException(
        ErrorCode.STALE_QUOTE,
        ErrorCode.STALE_QUOTE.defaultMessage(),
        new ErrorMetadata("error.quote.stale", "STALE_QUOTE"),
        Map.of("symbol", symbol, "reason", "QUOTE_SNAPSHOT_NOT_FOUND")
    );
  }

  private BusinessException staleQuote(String symbol, FepQuoteSnapshotResult snapshot, long snapshotAgeMs) {
    return new BusinessException(
        ErrorCode.STALE_QUOTE,
        ErrorCode.STALE_QUOTE.defaultMessage(),
        new ErrorMetadata("error.quote.stale", "STALE_QUOTE"),
        Map.of(
            "symbol", symbol,
            "snapshotAgeMs", snapshotAgeMs,
            "quoteSnapshotId", snapshot.quoteSnapshotId(),
            "quoteAsOf", snapshot.quoteAsOf().toString(),
            "quoteSourceMode", snapshot.quoteSourceMode().name()
        )
    );
  }

  private BigDecimal resolveMarketPrice(FepQuoteSnapshotResult snapshot) {
    Long rawMarketPrice = firstNonNull(snapshot.lastTrade(), snapshot.bestAsk(), snapshot.bestBid());
    if (rawMarketPrice == null) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "marketPrice is required in latest quote snapshot response"
      );
    }
    return BigDecimal.valueOf(rawMarketPrice).setScale(4);
  }

  private AccountPositionResult toInquiryAccountPositionResult(
      Account account,
      Long memberId,
      String symbol,
      Optional<Position> positionOptional
  ) {
    QuoteValuation quoteValuation = loadInquiryQuoteValuation(symbol);
    return toInquiryAccountPositionResult(
        account,
        memberId,
        symbol,
        positionOptional,
        quoteValuation
    );
  }

  private AccountPositionResult toInquiryAccountPositionResult(
      Account account,
      Long memberId,
      Position position,
      QuoteValuation quoteValuation
  ) {
    return toInquiryAccountPositionResult(account, memberId, position, quoteValuation, null);
  }

  private AccountPositionResult toInquiryAccountPositionResult(
      Account account,
      Long memberId,
      Position position,
      QuoteValuation quoteValuation,
      BigDecimal realizedPnlDaily
  ) {
    return toInquiryAccountPositionResult(
        account,
        memberId,
        position.getSymbol(),
        Optional.of(position),
        quoteValuation,
        realizedPnlDaily
    );
  }

  private AccountPositionResult toInquiryAccountPositionResult(
      Account account,
      Long memberId,
      String symbol,
      Optional<Position> positionOptional,
      QuoteValuation quoteValuation
  ) {
    return toInquiryAccountPositionResult(account, memberId, symbol, positionOptional, quoteValuation, null);
  }

  private AccountPositionResult toInquiryAccountPositionResult(
      Account account,
      Long memberId,
      String symbol,
      Optional<Position> positionOptional,
      QuoteValuation quoteValuation,
      BigDecimal precomputedRealizedPnlDaily
  ) {
    BigDecimal quantity = scale(positionOptional.map(Position::getQty).orElse(BigDecimal.ZERO));
    BigDecimal availableQuantity = quantity;
    BigDecimal avgPrice = resolveAvgPrice(positionOptional, quantity);
    BigDecimal unrealizedPnl = resolveUnrealizedPnl(quantity, avgPrice, quoteValuation);
    BigDecimal realizedPnlDaily = precomputedRealizedPnlDaily != null
        ? resolveRealizedPnlDaily(quoteValuation, precomputedRealizedPnlDaily)
        : resolveRealizedPnlDaily(account.getId(), symbol, quoteValuation);

    return AccountPositionResult.of(
        account.getId(),
        memberId,
        symbol,
        quantity,
        availableQuantity,
        account.getCashBalance(),
        account.getCurrency(),
        resolveAsOf(account, positionOptional),
        avgPrice,
        quoteValuation.marketPrice(),
        quoteValuation.quoteSnapshotId(),
        quoteValuation.quoteAsOf(),
        quoteValuation.quoteSourceMode(),
        unrealizedPnl,
        realizedPnlDaily,
        quoteValuation.valuationStatus(),
        quoteValuation.valuationUnavailableReason()
    );
  }

  private BigDecimal resolveAvgPrice(Optional<Position> positionOptional, BigDecimal quantity) {
    if (quantity.signum() == 0) {
      return null;
    }
    Position position = positionOptional.orElseThrow(() -> new BusinessException(
        ErrorCode.CONTRACT_VALIDATION_FAILED,
        "position row is required when quantity is positive"
    ));
    return scale(position.getAvgPrice());
  }

  private BigDecimal resolveUnrealizedPnl(BigDecimal quantity, BigDecimal avgPrice, QuoteValuation quoteValuation) {
    if (!quoteValuation.isFresh()) {
      return null;
    }
    if (quantity.signum() == 0) {
      return zero();
    }
    if (avgPrice == null || quoteValuation.marketPrice() == null) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "avgPrice and marketPrice are required for unrealized PnL"
      );
    }
    return scale(quoteValuation.marketPrice().subtract(avgPrice).multiply(quantity));
  }

  private BigDecimal resolveRealizedPnlDaily(Long accountId, String symbol, QuoteValuation quoteValuation) {
    if (!quoteValuation.isFresh()) {
      return null;
    }
    return calculateRealizedPnlDaily(accountId, symbol);
  }

  private BigDecimal resolveRealizedPnlDaily(QuoteValuation quoteValuation, BigDecimal realizedPnlDaily) {
    if (!quoteValuation.isFresh()) {
      return null;
    }
    return realizedPnlDaily != null ? scale(realizedPnlDaily) : zero();
  }

  private Map<String, BigDecimal> loadInquiryRealizedPnlDailyBySymbol(
      Long accountId,
      List<Position> positions,
      Map<String, QuoteValuation> quoteValuations
  ) {
    if (positions.isEmpty()) {
      return Map.of();
    }

    List<String> freshSymbols = positions.stream()
        .map(Position::getSymbol)
        .filter(symbol -> {
          QuoteValuation quoteValuation = quoteValuations.get(symbol);
          return quoteValuation != null && quoteValuation.isFresh();
        })
        .distinct()
        .toList();
    if (freshSymbols.isEmpty()) {
      return Map.of();
    }

    Instant from = startOfLimitWindowDay();
    Instant to = startOfNextLimitWindowDay();
    List<Execution> sameDayExecutions = executionRepository
        .findAllByAccountIdAndSymbolInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderBySymbolAscExecutedAtAscIdAsc(
        accountId,
        freshSymbols,
        from,
        to
    );
    java.util.LinkedHashMap<String, java.util.List<Execution>> sameDayExecutionsBySymbol = new java.util.LinkedHashMap<>();
    for (String symbol : freshSymbols) {
      sameDayExecutionsBySymbol.put(symbol, new java.util.ArrayList<>());
    }
    for (Execution execution : sameDayExecutions) {
      java.util.List<Execution> symbolExecutions = sameDayExecutionsBySymbol.get(execution.getSymbol());
      if (symbolExecutions != null) {
        symbolExecutions.add(execution);
      }
    }

    java.util.LinkedHashMap<String, BigDecimal> realizedPnlDailyBySymbol = new java.util.LinkedHashMap<>();
    java.util.LinkedHashMap<String, Position> positionsBySymbol = new java.util.LinkedHashMap<>();
    for (Position position : positions) {
      if (freshSymbols.contains(position.getSymbol())) {
        positionsBySymbol.put(position.getSymbol(), position);
      }
    }
    java.util.ArrayList<String> fallbackSymbols = new java.util.ArrayList<>();
    for (String symbol : freshSymbols) {
      Position position = positionsBySymbol.get(symbol);
      BigDecimal optimizedRealizedPnlDaily = tryCalculateSameDayRealizedPnlFromCurrentPosition(
          position,
          sameDayExecutionsBySymbol.getOrDefault(symbol, List.of())
      );
      if (optimizedRealizedPnlDaily != null) {
        realizedPnlDailyBySymbol.put(symbol, optimizedRealizedPnlDaily);
      } else {
        fallbackSymbols.add(symbol);
      }
    }
    if (!fallbackSymbols.isEmpty()) {
      List<Execution> historicalExecutions = executionRepository.findAllByAccountIdAndSymbolInOrderBySymbolAscExecutedAtAscIdAsc(
          accountId,
          fallbackSymbols
      );
      java.util.LinkedHashMap<String, java.util.List<Execution>> historicalExecutionsBySymbol = new java.util.LinkedHashMap<>();
      for (String symbol : fallbackSymbols) {
        historicalExecutionsBySymbol.put(symbol, new java.util.ArrayList<>());
      }
      for (Execution execution : historicalExecutions) {
        java.util.List<Execution> symbolExecutions = historicalExecutionsBySymbol.get(execution.getSymbol());
        if (symbolExecutions != null) {
          symbolExecutions.add(execution);
        }
      }
      for (String symbol : fallbackSymbols) {
        realizedPnlDailyBySymbol.put(
            symbol,
            calculateRealizedPnlDaily(historicalExecutionsBySymbol.getOrDefault(symbol, List.of()))
        );
      }
    }
    return realizedPnlDailyBySymbol;
  }

  private BigDecimal tryCalculateSameDayRealizedPnlFromCurrentPosition(
      Position currentPosition,
      List<Execution> sameDayExecutions
  ) {
    if (currentPosition == null) {
      return null;
    }
    BigDecimal currentQuantity = scale(currentPosition.getQty());
    if (currentQuantity == null || currentQuantity.signum() <= 0) {
      return null;
    }
    if (sameDayExecutions.isEmpty()) {
      return zero();
    }

    BigDecimal runningQuantity = currentQuantity;
    BigDecimal runningAvgPrice = scale(currentPosition.getAvgPrice());
    if (runningAvgPrice == null) {
      return null;
    }
    BigDecimal realizedPnlDaily = zero();

    List<Execution> reverseChronologicalExecutions = sameDayExecutions.stream()
        .sorted(Comparator.comparing(Execution::getExecutedAt).thenComparing(Execution::getId).reversed())
        .toList();
    for (Execution execution : reverseChronologicalExecutions) {
      String side = normalizeExecutionSide(execution.getSide());
      BigDecimal execQty = scale(execution.getExecQty());
      BigDecimal execPrice = scale(execution.getExecPrice());
      if (execQty == null || execPrice == null) {
        return null;
      }
      if ("SELL".equals(side)) {
        if (runningQuantity.signum() == 0 || runningAvgPrice.signum() == 0) {
          return null;
        }
        realizedPnlDaily = realizedPnlDaily.add(scale(execPrice.subtract(runningAvgPrice).multiply(execQty)));
        runningQuantity = scale(runningQuantity.add(execQty));
        continue;
      }
      if ("BUY".equals(side)) {
        BigDecimal previousQuantity = scale(runningQuantity.subtract(execQty));
        if (previousQuantity == null || previousQuantity.signum() < 0) {
          return null;
        }
        if (previousQuantity.signum() == 0) {
          runningQuantity = zero();
          runningAvgPrice = zero();
          continue;
        }
        BigDecimal totalCostAfterBuy = scale(runningAvgPrice.multiply(runningQuantity));
        BigDecimal previousTotalCost = scale(totalCostAfterBuy.subtract(execPrice.multiply(execQty)));
        if (previousTotalCost == null || previousTotalCost.signum() < 0) {
          return null;
        }
        runningQuantity = previousQuantity;
        runningAvgPrice = scale(previousTotalCost.divide(previousQuantity, DECIMAL_SCALE, RoundingMode.HALF_UP));
        continue;
      }
      return null;
    }

    return scale(realizedPnlDaily);
  }

  private BigDecimal calculateRealizedPnlDaily(Long accountId, String symbol) {
    return calculateRealizedPnlDaily(
        executionRepository.findAllByAccountIdAndSymbolOrderByExecutedAtAsc(accountId, symbol)
    );
  }

  private BigDecimal calculateRealizedPnlDaily(List<Execution> executions) {
    Instant from = startOfLimitWindowDay();
    Instant to = startOfNextLimitWindowDay();
    List<Execution> orderedExecutions = executions.stream()
        .sorted(Comparator.comparing(Execution::getExecutedAt).thenComparing(Execution::getId))
        .toList();

    if (orderedExecutions.isEmpty()) {
      return zero();
    }

    Execution firstExecution = orderedExecutions.get(0);
    Position rebuilt = Position.of(firstExecution.getAccountId(), firstExecution.getSymbol(), zero(), zero());
    BigDecimal realizedPnlDaily = zero();
    for (Execution execution : orderedExecutions) {
      String side = normalizeExecutionSide(execution.getSide());
      if ("BUY".equals(side)) {
        rebuilt.applyBuy(execution.getExecQty(), execution.getExecPrice());
        continue;
      }
      if ("SELL".equals(side)) {
        BigDecimal avgPriceAtSell = scale(rebuilt.getAvgPrice());
        if (!execution.getExecutedAt().isBefore(from) && execution.getExecutedAt().isBefore(to)) {
          BigDecimal realizedPnl = execution.getExecPrice()
              .subtract(avgPriceAtSell)
              .multiply(execution.getExecQty());
          realizedPnlDaily = realizedPnlDaily.add(scale(realizedPnl));
        }
        rebuilt.applySell(execution.getExecQty());
        continue;
      }
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "unsupported execution side for realized pnl calculation: " + execution.getSide()
      );
    }
    return scale(realizedPnlDaily);
  }

  private String normalizeExecutionSide(String side) {
    if (side == null || side.isBlank()) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "execution side is required for realized pnl calculation"
      );
    }
    return side.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private BigDecimal scale(BigDecimal value) {
    if (value == null) {
      return null;
    }
    return value.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal zero() {
    return BigDecimal.ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
  }

  private Long firstNonNull(Long... candidates) {
    for (Long candidate : candidates) {
      if (candidate != null) {
        return candidate;
      }
    }
    return null;
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

  private record QuoteValuation(
      ValuationStatus valuationStatus,
      ValuationUnavailableReason valuationUnavailableReason,
      BigDecimal marketPrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode
  ) {

    private static QuoteValuation fresh(
        BigDecimal marketPrice,
        String quoteSnapshotId,
        Instant quoteAsOf,
        FepQuoteSourceMode quoteSourceMode
    ) {
      return new QuoteValuation(
          ValuationStatus.FRESH,
          null,
          marketPrice,
          quoteSnapshotId,
          quoteAsOf,
          quoteSourceMode
      );
    }

    private static QuoteValuation stale(
        String quoteSnapshotId,
        Instant quoteAsOf,
        FepQuoteSourceMode quoteSourceMode
    ) {
      return new QuoteValuation(
          ValuationStatus.STALE,
          ValuationUnavailableReason.STALE_QUOTE,
          null,
          quoteSnapshotId,
          quoteAsOf,
          quoteSourceMode
      );
    }

    private static QuoteValuation unavailable(ValuationUnavailableReason reason) {
      return new QuoteValuation(
          ValuationStatus.UNAVAILABLE,
          reason,
          null,
          null,
          null,
          null
      );
    }

    private boolean isFresh() {
      return valuationStatus == ValuationStatus.FRESH;
    }
  }
}
