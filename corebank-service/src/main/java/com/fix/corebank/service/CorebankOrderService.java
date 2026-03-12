package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.corebank.domain.AccountStatus;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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

  @Value("${recovery.max-retry-count:5}")
  private int maxRetryCount = 5;

  @Transactional(readOnly = true)
  public PortfolioResult getPortfolio(PortfolioQueryCommand command) {
    Account account = accountRepository.findById(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    Position position = positionRepository.findByAccountIdAndSymbol(command.getAccountId(), command.getSymbol())
        .orElse(Position.of(command.getAccountId(), command.getSymbol(), BigDecimal.ZERO, BigDecimal.ZERO));

    BigDecimal todaySellQty = executionRepository.sumSellQuantityByAccountAndSymbolBetween(
        command.getAccountId(),
        command.getSymbol(),
        startOfUtcDay(),
        startOfNextUtcDay()
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
    try {
      FepOrderResult gatewayStatus = fepClient.queryOrderStatus(
          order.clOrdId(),
          correlationId("requery", order.clOrdId())
      );
      CorebankOrderPersistenceService.OrderSnapshot updatedOrder =
          orderPersistenceService.updateOrderState(
              order.clOrdId(),
              gatewayStatus.ordStatus().name(),
              externalSyncStatusForRequery(gatewayStatus.ordStatus(), command.getAttemptCount()),
              resolveFepReferenceId(order, gatewayStatus.fepOrderId()),
              failureReasonForRequery(gatewayStatus)
          );
      String requeryMessage = failureReasonForRequery(gatewayStatus);
      return mapToRequeryResult(
          updatedOrder,
          requeryMessage,
          classifyRequeryOutcome(gatewayStatus.ordStatus(), command.getAttemptCount())
      );
    } catch (BusinessException ex) {
      if (isRetriableRequeryFailure(ex)) {
        CorebankOrderPersistenceService.OrderSnapshot currentOrder = order;
        if (!isTerminalOrderStatus(order.status())) {
          currentOrder = orderPersistenceService.updateOrderState(
              order.clOrdId(),
              order.status(),
              externalSyncStatusForRetriableFailure(command.getAttemptCount()),
              order.fepReferenceId(),
              failureReason(ex)
          );
        }
        return mapToRequeryResult(
            currentOrder,
            ex.getMessage(),
            classifyRetriableFailure(order.status(), command.getAttemptCount())
        );
      }
      throw ex;
    }
  }

  private InternalOrderResult createFreshOrder(InternalOrderCreateCommand command) {
    try {
      CorebankOrderPersistenceService.PendingOrderSubmission pendingOrder =
          orderPersistenceService.prepareOrderSubmission(command);
      try {
        FepOrderResult gatewayOrder = fepClient.submitOrder(
            toFepPayload(pendingOrder),
            correlationId("submit", command.getClOrdId())
        );
        CorebankOrderPersistenceService.OrderSnapshot updatedOrder =
            orderPersistenceService.updateOrderState(
                pendingOrder.clOrdId(),
                gatewayOrder.ordStatus().name(),
                Order.EXTERNAL_SYNC_CONFIRMED,
                gatewayOrder.fepOrderId(),
                null
            );
        return mapToOrderResult(updatedOrder, false);
      } catch (BusinessException ex) {
        orderPersistenceService.updateOrderState(
            pendingOrder.clOrdId(),
            pendingOrder.status(),
            Order.EXTERNAL_SYNC_FAILED,
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

  private InternalOrderResult mapToOrderResult(CorebankOrderPersistenceService.OrderSnapshot order, boolean idempotent) {
    return InternalOrderResult.of(
        order.orderId(),
        order.clOrdId(),
        order.status(),
        order.externalSyncStatus(),
        idempotent,
        order.orderQty()
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
      case REJECTED -> new RequerySignal(false, true, attemptCount, maxRetryCount);
      case FILLED, PARTIALLY_FILLED, CANCELED -> new RequerySignal(false, false, attemptCount, maxRetryCount);
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

  private String externalSyncStatusForRequery(FepOrdStatus ordStatus, int attemptCount) {
    return switch (ordStatus) {
      case FILLED, PARTIALLY_FILLED, CANCELED -> Order.EXTERNAL_SYNC_CONFIRMED;
      case REJECTED -> Order.EXTERNAL_SYNC_ESCALATED;
      case UNKNOWN, PENDING, MALFORMED -> externalSyncStatusForRetriableFailure(attemptCount);
    };
  }

  private String externalSyncStatusForRetriableFailure(int attemptCount) {
    return isEscalationThresholdReached(attemptCount)
        ? Order.EXTERNAL_SYNC_ESCALATED
        : Order.EXTERNAL_SYNC_FAILED;
  }

  private boolean isEscalationThresholdReached(int attemptCount) {
    return attemptCount >= maxRetryCount;
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

  private String failureReasonForRequery(FepOrderResult result) {
    return switch (result.ordStatus()) {
      case FILLED, PARTIALLY_FILLED, CANCELED -> null;
      case REJECTED -> firstNonBlank(result.rejectReason(), result.message(), "REJECTED");
      case UNKNOWN, PENDING -> result.message();
      case MALFORMED -> firstNonBlank(result.parseError(), result.message());
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

  private Instant startOfUtcDay() {
    return LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  private Instant startOfNextUtcDay() {
    return LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
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
}
