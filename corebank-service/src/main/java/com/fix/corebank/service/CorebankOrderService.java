package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
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

  public AccountPositionResult getAccountPosition(AccountPositionQueryCommand command) {
    CorebankAccountPositionQueryService.AccountPositionSnapshot snapshot =
        accountPositionQueryService.getOwnedAccountPosition(command);
    QuoteValuation quoteValuation = loadFreshQuoteValuation(command.getSymbol());
    Optional<Position> positionOptional = snapshot.position();
    BigDecimal quantity = positionOptional.map(Position::getQty).orElse(BigDecimal.ZERO);
    BigDecimal availableQuantity = quantity;

    return AccountPositionResult.of(
        snapshot.account().getId(),
        command.getMemberId(),
        command.getSymbol(),
        quantity,
        availableQuantity,
        snapshot.account().getCashBalance(),
        snapshot.account().getCurrency(),
        resolveAsOf(snapshot.account(), positionOptional),
        quoteValuation.marketPrice(),
        quoteValuation.quoteSnapshotId(),
        quoteValuation.quoteAsOf(),
        quoteValuation.quoteSourceMode()
    );
  }

  public List<AccountPositionResult> getAccountPositions(AccountPositionsQueryCommand command) {
    CorebankAccountPositionQueryService.AccountPositionsSnapshot snapshot =
        accountPositionQueryService.getOwnedPositiveAccountPositions(command);
    Map<String, QuoteValuation> quoteValuations = loadFreshQuoteValuations(
        snapshot.positions().stream()
            .map(Position::getSymbol)
            .toList()
    );

    return snapshot.positions().stream()
        .map(position -> {
          QuoteValuation quoteValuation = quoteValuations.get(position.getSymbol());
          return AccountPositionResult.of(
              snapshot.account().getId(),
              command.getMemberId(),
              position.getSymbol(),
              position.getQty(),
              position.getQty(),
              snapshot.account().getCashBalance(),
              snapshot.account().getCurrency(),
              resolveAsOf(snapshot.account(), Optional.of(position)),
              quoteValuation.marketPrice(),
              quoteValuation.quoteSnapshotId(),
              quoteValuation.quoteAsOf(),
              quoteValuation.quoteSourceMode()
          );
        })
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

  public List<AccountPositionResult> getAccountPositions(AccountStatusQueryCommand command) {
    CorebankAccountPositionQueryService.AccountPositionsSnapshot snapshot =
        accountPositionQueryService.getOwnedAccountPositions(command);
    Map<String, QuoteValuation> quoteValuations = loadFreshQuoteValuations(
        snapshot.positions().stream()
            .map(Position::getSymbol)
            .toList()
    );

    return snapshot.positions().stream()
        .map(position -> {
          QuoteValuation quoteValuation = quoteValuations.get(position.getSymbol());
          return AccountPositionResult.of(
              snapshot.account().getId(),
              snapshot.account().getMemberId(),
              position.getSymbol(),
              position.getQty(),
              position.getQty(),
              snapshot.account().getCashBalance(),
              snapshot.account().getCurrency(),
              resolveAsOf(snapshot.account(), Optional.of(position)),
              quoteValuation.marketPrice(),
              quoteValuation.quoteSnapshotId(),
              quoteValuation.quoteAsOf(),
              quoteValuation.quoteSourceMode()
          );
        })
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
          .map(existing -> resolveIdempotentReplay(existing, command))
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
    return orderType.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private String failureReason(BusinessException ex) {
    if (ex.getMetadata() != null && ex.getMetadata().operatorCode() != null && !ex.getMetadata().operatorCode().isBlank()) {
      return ex.getMetadata().operatorCode();
    }
    return ex.getErrorCode().code();
  }

  private QuoteValuation loadFreshQuoteValuation(String symbol) {
    FepQuoteSnapshotResult snapshot = queryLatestQuoteSnapshot(symbol);
    return toFreshQuoteValuation(symbol, snapshot);
  }

  private Map<String, QuoteValuation> loadFreshQuoteValuations(List<String> symbols) {
    if (symbols.isEmpty()) {
      return Map.of();
    }

    Map<String, FepQuoteSnapshotResult> snapshots = queryLatestQuoteSnapshots(symbols);
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
    return new QuoteValuation(
        resolveMarketPrice(snapshot),
        snapshot.quoteSnapshotId(),
        snapshot.quoteAsOf(),
        snapshot.quoteSourceMode()
    );
  }

  private FepQuoteSnapshotResult queryLatestQuoteSnapshot(String symbol) {
    try {
      return fepQuoteSnapshotClient.queryLatestQuoteSnapshot(
          symbol,
          corebankMarketDataProperties.getQuoteSourceMode(),
          correlationId("quote", symbol)
      );
    } catch (BusinessException ex) {
      throw translateQuoteSnapshotFailure(symbol, ex);
    }
  }

  private Map<String, FepQuoteSnapshotResult> queryLatestQuoteSnapshots(List<String> symbols) {
    try {
      return fepQuoteSnapshotClient.queryLatestQuoteSnapshots(
          symbols,
          corebankMarketDataProperties.getQuoteSourceMode(),
          correlationId("quote-batch", String.join(",", symbols))
      );
    } catch (BusinessException ex) {
      if (ex.getErrorCode() == ErrorCode.NOT_FOUND && !symbols.isEmpty()) {
        throw missingQuoteSnapshot(symbols.get(0));
      }
      throw ex;
    }
  }

  private void validateFreshMarketQuote(InternalOrderCreateCommand command) {
    if (!FepOrderType.MARKET.name().equals(normalizeOrderType(command.getOrderType()))) {
      return;
    }
    if (command.getQuoteAsOf() == null) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "quoteAsOf is required for MARKET orders");
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
      BigDecimal marketPrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode
  ) {
  }
}
