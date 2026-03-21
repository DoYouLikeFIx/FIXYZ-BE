package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.corebank.domain.AccountStatus;
import com.fix.corebank.entity.Account;
import com.fix.corebank.entity.AccountStatusEvent;
import com.fix.corebank.entity.Execution;
import com.fix.corebank.entity.JournalEntry;
import com.fix.corebank.entity.LedgerEntry;
import com.fix.corebank.entity.LedgerEntryRef;
import com.fix.corebank.entity.Order;
import com.fix.corebank.entity.Position;
import com.fix.corebank.exception.order.DailySellLimitExceededException;
import com.fix.corebank.exception.order.InsufficientPositionException;
import com.fix.corebank.exception.order.PositionLockContentionException;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.AccountStatusEventRepository;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.JournalEntryRepository;
import com.fix.corebank.repository.LedgerEntryRefRepository;
import com.fix.corebank.repository.LedgerEntryRepository;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.vo.AccountStatusTransitionCommand;
import com.fix.corebank.vo.AccountStatusTransitionResult;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CorebankOrderPersistenceService {

  private static final int MONEY_SCALE = 4;
  private static final String LOCAL_EXECUTION_STATUS = "PENDING";
  private static final String LOCAL_EXECUTION_RESULT = "FILLED";
  private static final String JOURNAL_ENTRY_TYPE_ORDER_EXECUTED = "ORDER_EXECUTED";
  private static final String LEDGER_TYPE_CASH = "CASH";
  private static final String LEDGER_TYPE_POSITION = "POSITION";
  private static final String LEDGER_DIRECTION_DEBIT = "DR";
  private static final String LEDGER_DIRECTION_CREDIT = "CR";

  private final AccountRepository accountRepository;
  private final AccountStatusEventRepository accountStatusEventRepository;
  private final OrderRepository orderRepository;
  private final PositionRepository positionRepository;
  private final ExecutionRepository executionRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final LedgerEntryRefRepository ledgerEntryRefRepository;
  private final PositionLockMetrics positionLockMetrics;
  private final CorebankOppositeBookQueryService oppositeBookQueryService;
  private final MarketOrderSweepMatcher marketOrderSweepMatcher;
  private final OrderPreparationLockHook orderPreparationLockHook;
  private final OrderPostingTransactionHook orderPostingTransactionHook;
  private Clock limitWindowClock = Clock.systemUTC();

  @Value("${corebank.order.limit-window-zone:UTC}")
  private String limitWindowZone = "UTC";

  @Transactional(readOnly = true)
  public Optional<OrderSnapshot> findOrder(String clOrdId) {
    return orderRepository.findByClOrdId(clOrdId).map(OrderSnapshot::from);
  }

  @Transactional(readOnly = true)
  public OrderSnapshot getRequiredOrder(String clOrdId) {
    return findOrder(clOrdId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "order not found"));
  }

  @Transactional(readOnly = true)
  public AccountOrderHistoryPage findAccountOrderHistory(Long accountId, int page, int size) {
    Pageable pageable = PageRequest.of(
        page,
        size,
        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
    );
    Page<Order> orderPage = orderRepository.findByAccountId(accountId, pageable);

    List<AccountOrderHistoryRow> content = orderPage.getContent().stream()
        .map(order -> new AccountOrderHistoryRow(
            order.getSymbol(),
            order.getSide(),
            order.getOrderQty(),
            order.getOrderPrice(),
            order.getStatus(),
            order.getClOrdId(),
            order.getCreatedAt()
        ))
        .toList();

    return new AccountOrderHistoryPage(
        content,
        orderPage.getTotalElements(),
        orderPage.getTotalPages(),
        orderPage.getNumber(),
        orderPage.getSize()
    );
  }

  @Transactional
  public PendingOrderSubmission prepareOrderSubmission(InternalOrderCreateCommand command) {
    if (!accountRepository.existsById(command.getAccountId())) {
      throw new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found");
    }

    String side = normalizeSide(command.getSide());
    String orderType = normalizeOrderType(command.getOrderType());
    long waitStartedAtNanos = System.nanoTime();
    Position position;
    try {
      position = lockPositionForUpdate(command.getAccountId(), command.getSymbol());
    } finally {
      positionLockMetrics.recordWait(waitStartedAtNanos);
    }
    positionLockMetrics.recordHoldOnTransactionCompletion(System.nanoTime());
    BigDecimal availableQty = resolveAvailableQuantity(position);

    BigDecimal todaySellQty = executionRepository.sumSellQuantityByAccountAndSymbolBetween(
        command.getAccountId(),
        command.getSymbol(),
        startOfLimitWindowDay(),
        startOfNextLimitWindowDay()
    );

    orderPreparationLockHook.afterPositionLock(command.getAccountId(), command.getSymbol());

    Account account = accountRepository.findByIdForUpdate(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));
    ensureOrderEligibleAccountStatus(account);

    if ("SELL".equals(side)) {
      if (command.getQuantity().compareTo(availableQty) > 0) {
        throw new InsufficientPositionException(
            command.getAccountId(),
            command.getSymbol(),
            availableQty,
            command.getQuantity()
        );
      }
      BigDecimal afterSell = todaySellQty.add(command.getQuantity());
      if (afterSell.compareTo(account.getDailySellLimit()) > 0) {
        throw new DailySellLimitExceededException(
            command.getAccountId(),
            command.getSymbol(),
            command.getQuantity(),
            todaySellQty,
            account.getDailySellLimit()
        );
      }
    }

    if ("MARKET".equals(orderType)) {
      return prepareMarketOrderSubmission(command, account, position, side);
    }

    BigDecimal orderPrice = resolveOrderPrice(orderType, command);
    BigDecimal referencePrice = resolveReferencePrice(orderType, command);

    Order candidateOrder = Order.accepted(
        command.getAccountId(),
        command.getClOrdId(),
        command.getSymbol(),
        side,
        orderType,
        command.getQuantity(),
        orderPrice,
        command.getPreTradePrice(),
        command.getQuoteSnapshotId(),
        command.getQuoteAsOf(),
        command.getQuoteSourceMode()
    );
    Order savedOrder = orderRepository.saveAndFlush(candidateOrder);
    if (savedOrder == null) {
      savedOrder = candidateOrder;
    }

    Instant executedAt = Instant.now();
    BigDecimal executedQty = normalizeMoney(command.getQuantity());
    BigDecimal executedPrice = normalizeMoney(referencePrice);
    BigDecimal leavesQty = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal grossAmount = calculateGrossAmount(executedQty, executedPrice);

    executionRepository.saveAndFlush(Execution.of(
        savedOrder.getId(),
        savedOrder.getAccountId(),
        savedOrder.getClOrdId(),
        savedOrder.getSymbol(),
        savedOrder.getSide(),
        executedQty,
        executedPrice,
        savedOrder.getQuoteSnapshotId(),
        savedOrder.getQuoteAsOf(),
        savedOrder.getQuoteSourceMode(),
        executedAt
    ));

    applyCanonicalPosting(account, position, side, executedQty, executedPrice, grossAmount);
    savedOrder.completeExecution(
        LOCAL_EXECUTION_STATUS,
        LOCAL_EXECUTION_RESULT,
        executedQty,
        leavesQty,
        executedPrice,
        executedAt
    );
    orderPostingTransactionHook.afterPostingMutation(savedOrder, account, position);
    appendExecutionPosting(savedOrder, grossAmount);
    orderRepository.flush();
    return PendingOrderSubmission.from(savedOrder, account);
  }

  private PendingOrderSubmission prepareMarketOrderSubmission(
      InternalOrderCreateCommand command,
      Account takerAccount,
      Position takerPosition,
      String side
  ) {
    List<Order> makerOrders = oppositeBookQueryService.lockRestingLimitOrders(command.getSymbol(), side);
    MarketOrderSweepMatcher.MarketSweepMatchResult matchResult = marketOrderSweepMatcher.match(
        command.getQuantity(),
        makerOrders.stream()
            .map(oppositeBookQueryService::toEntry)
            .toList()
    );
    if (matchResult.rejected()) {
      throw noLiquidity(command.getSymbol(), side, command.getQuantity());
    }

    Order takerOrder = orderRepository.saveAndFlush(Order.accepted(
        command.getAccountId(),
        command.getClOrdId(),
        command.getSymbol(),
        side,
        "MARKET",
        command.getQuantity(),
        null,
        command.getPreTradePrice(),
        command.getQuoteSnapshotId(),
        command.getQuoteAsOf(),
        command.getQuoteSourceMode()
    ));
    if (takerOrder == null) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "failed to persist market taker order");
    }

    Instant executedAt = Instant.now();
    BigDecimal totalGross = zeroMoney();
    for (MarketOrderSweepMatcher.MarketSweepFill fill : matchResult.fills()) {
      Order makerOrder = requireMatchedOrder(makerOrders, fill.makerOrderId());
      Account makerAccount = accountRepository.findByIdForUpdate(makerOrder.getAccountId())
          .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "maker account not found"));
      Position makerPosition = lockPositionForUpdate(makerOrder.getAccountId(), makerOrder.getSymbol());
      BigDecimal fillQty = normalizeMoney(fill.executedQty());
      BigDecimal fillPrice = normalizeMoney(fill.executedPrice());
      BigDecimal fillGross = calculateGrossAmount(fillQty, fillPrice);

      applyCanonicalPosting(takerAccount, takerPosition, side, fillQty, fillPrice, fillGross);
      applyCanonicalPosting(makerAccount, makerPosition, makerOrder.getSide(), fillQty, fillPrice, fillGross);

      saveExecutionFill(takerOrder, fillQty, fillPrice, executedAt);
      saveExecutionFill(makerOrder, fillQty, fillPrice, executedAt);

      applyMatchSummary(makerOrder, fillQty, fillPrice, fill.remainingMakerQty(), executedAt);
      orderPostingTransactionHook.afterPostingMutation(makerOrder, makerAccount, makerPosition);
      appendExecutionPosting(makerOrder, fillGross);
      totalGross = totalGross.add(fillGross).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    takerOrder.completeExecution(
        LOCAL_EXECUTION_STATUS,
        matchResult.executionResult(),
        matchResult.executedQty(),
        matchResult.leavesQty(),
        matchResult.executedPrice(),
        executedAt
    );
    orderPostingTransactionHook.afterPostingMutation(takerOrder, takerAccount, takerPosition);
    appendExecutionPosting(takerOrder, totalGross);
    orderRepository.flush();
    return PendingOrderSubmission.from(takerOrder, takerAccount);
  }

  @Transactional
  public AccountStatusTransitionResult transitionAccountStatus(AccountStatusTransitionCommand command) {
    Account account = accountRepository.findByIdForUpdate(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    if (!account.getMemberId().equals(command.getMemberId())) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP, "forbidden account ownership");
    }

    AccountStatus previousStatus = parseAccountStatus(account.getStatus());
    AccountStatus nextStatus = parseAccountStatus(command.getTargetStatus());
    if (previousStatus == nextStatus) {
      return AccountStatusTransitionResult.of(
          account.getId(),
          account.getMemberId(),
          previousStatus.name(),
          previousStatus.name(),
          false,
          null,
          command.getReason(),
          command.getActor(),
          command.getContext(),
          account.getUpdatedAt()
      );
    }

    account.updateStatus(nextStatus.name());
    accountRepository.flush();

    AccountStatusEvent event = accountStatusEventRepository.save(AccountStatusEvent.of(
        account.getId(),
        account.getMemberId(),
        previousStatus.name(),
        nextStatus.name(),
        command.getReason(),
        command.getActor(),
        command.getContext(),
        command.getCorrelationId()
    ));

    return AccountStatusTransitionResult.of(
        account.getId(),
        account.getMemberId(),
        previousStatus.name(),
        nextStatus.name(),
        true,
        event.getId(),
        command.getReason(),
        command.getActor(),
        command.getContext(),
        account.getUpdatedAt()
    );
  }

  @Transactional
  public OrderSnapshot updateOrderState(
      String clOrdId,
      String status,
      String externalSyncStatus,
      String fepReferenceId,
      String failureReason
  ) {
    Order order = orderRepository.findByClOrdId(clOrdId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "order not found"));
    order.updateState(status, externalSyncStatus, fepReferenceId, failureReason);
    orderRepository.flush();
    return OrderSnapshot.from(order);
  }

  @Transactional
  public OrderStateUpdateResult updateOrderStateIfSnapshotMatches(
      OrderSnapshot expectedOrder,
      String status,
      String externalSyncStatus,
      String fepReferenceId,
      String failureReason
  ) {
    int updatedRows = orderRepository.updateStateIfVersionMatches(
        expectedOrder.clOrdId(),
        expectedOrder.version(),
        status,
        externalSyncStatus,
        fepReferenceId,
        failureReason,
        Instant.now()
    );
    OrderSnapshot currentOrder = findOrder(expectedOrder.clOrdId()).orElse(null);
    return new OrderStateUpdateResult(currentOrder, updatedRows == 1);
  }

  private void appendExecutionPosting(Order order, BigDecimal grossAmount) {
    JournalEntry journalEntry = journalEntryRepository.save(
        JournalEntry.of(order.getId(), JOURNAL_ENTRY_TYPE_ORDER_EXECUTED, grossAmount, "canonical same-bank ledger posting")
    );
    if (journalEntry == null) {
      return;
    }

    if ("BUY".equals(order.getSide())) {
      saveLedgerEntryWithRef(journalEntry.getId(), order.getAccountId(), LEDGER_TYPE_POSITION, LEDGER_DIRECTION_DEBIT, grossAmount, order.getClOrdId());
      saveLedgerEntryWithRef(journalEntry.getId(), order.getAccountId(), LEDGER_TYPE_CASH, LEDGER_DIRECTION_CREDIT, grossAmount, order.getClOrdId());
      return;
    }

    saveLedgerEntryWithRef(journalEntry.getId(), order.getAccountId(), LEDGER_TYPE_CASH, LEDGER_DIRECTION_DEBIT, grossAmount, order.getClOrdId());
    saveLedgerEntryWithRef(journalEntry.getId(), order.getAccountId(), LEDGER_TYPE_POSITION, LEDGER_DIRECTION_CREDIT, grossAmount, order.getClOrdId());
  }

  private void saveExecutionFill(Order order, BigDecimal executedQty, BigDecimal executedPrice, Instant executedAt) {
    executionRepository.saveAndFlush(Execution.of(
        order.getId(),
        order.getAccountId(),
        order.getClOrdId(),
        order.getSymbol(),
        order.getSide(),
        executedQty,
        executedPrice,
        order.getQuoteSnapshotId(),
        order.getQuoteAsOf(),
        order.getQuoteSourceMode(),
        executedAt
    ));
  }

  private void applyMatchSummary(
      Order order,
      BigDecimal fillQty,
      BigDecimal fillPrice,
      BigDecimal leavesQty,
      Instant executedAt
  ) {
    BigDecimal currentExecutedQty = zeroIfNull(order.getExecutedQty());
    BigDecimal nextExecutedQty = currentExecutedQty.add(fillQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal nextExecutedPrice = weightedAveragePrice(currentExecutedQty, order.getExecutedPrice(), fillQty, fillPrice);
    BigDecimal normalizedLeavesQty = normalizeMoney(leavesQty);
    String executionResult = normalizedLeavesQty.signum() == 0 ? "FILLED" : "PARTIALLY_FILLED";
    order.completeExecution(
        executionResult,
        executionResult,
        nextExecutedQty,
        normalizedLeavesQty,
        nextExecutedPrice,
        executedAt
    );
  }

  private Position lockPositionForUpdate(Long accountId, String symbol) {
    try {
      return findOrCreatePositionForUpdate(accountId, symbol);
    } catch (RuntimeException ex) {
      if (isPositionLockAcquisitionFailure(ex)) {
        throw new PositionLockContentionException(accountId, symbol, ex);
      }
      throw ex;
    }
  }

  private Position findOrCreatePositionForUpdate(Long accountId, String symbol) {
    Optional<Position> existingPosition = positionRepository.findByAccountIdAndSymbolForUpdate(accountId, symbol);
    if (existingPosition.isPresent()) {
      return existingPosition.get();
    }

    Position createdPosition = Position.of(
        accountId,
        symbol,
        BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
        BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    );

    try {
      Position persistedPosition = positionRepository.saveAndFlush(createdPosition);
      return persistedPosition != null ? persistedPosition : createdPosition;
    } catch (DataIntegrityViolationException ex) {
      return positionRepository.findByAccountIdAndSymbolForUpdate(accountId, symbol)
          .orElseThrow(() -> ex);
    }
  }

  private boolean isPositionLockAcquisitionFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof CannotAcquireLockException
          || current instanceof PessimisticLockingFailureException
          || current instanceof jakarta.persistence.LockTimeoutException
          || current instanceof jakarta.persistence.PessimisticLockException
          || isPositionLockExceptionClassName(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean isPositionLockExceptionClassName(Throwable throwable) {
    String className = throwable.getClass().getName();
    return "org.hibernate.exception.LockAcquisitionException".equals(className)
        || "org.hibernate.PessimisticLockException".equals(className)
        || "org.springframework.dao.DeadlockLoserDataAccessException".equals(className)
        || "java.sql.SQLTransactionRollbackException".equals(className)
        || "com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException".equals(className);
  }

  private void saveLedgerEntryWithRef(
      Long journalEntryId,
      Long accountId,
      String ledgerType,
      String direction,
      BigDecimal amount,
      String clOrdId
  ) {
    LedgerEntry ledgerEntry = ledgerEntryRepository.save(
        LedgerEntry.of(journalEntryId, accountId, ledgerType, direction, amount)
    );
    if (ledgerEntry != null && ledgerEntry.getId() != null) {
      ledgerEntryRefRepository.save(LedgerEntryRef.of(ledgerEntry.getId(), "CL_ORD_ID", clOrdId));
    }
  }

  private void applyCanonicalPosting(
      Account account,
      Position position,
      String side,
      BigDecimal executedQty,
      BigDecimal executedPrice,
      BigDecimal grossAmount
  ) {
    if ("BUY".equals(side)) {
      account.debitCash(grossAmount);
      position.applyBuy(executedQty, executedPrice);
      return;
    }

    position.applySell(executedQty);
    account.creditCash(grossAmount);
  }

  private BigDecimal calculateGrossAmount(BigDecimal quantity, BigDecimal price) {
    return normalizeMoney(quantity.multiply(price));
  }

  private BigDecimal weightedAveragePrice(
      BigDecimal currentExecutedQty,
      BigDecimal currentExecutedPrice,
      BigDecimal fillQty,
      BigDecimal fillPrice
  ) {
    if (currentExecutedQty == null || currentExecutedQty.signum() == 0) {
      return normalizeMoney(fillPrice);
    }
    BigDecimal currentGross = currentExecutedQty.multiply(normalizeMoney(currentExecutedPrice));
    BigDecimal fillGross = fillQty.multiply(fillPrice);
    BigDecimal totalQty = currentExecutedQty.add(fillQty);
    return normalizeMoney(currentGross.add(fillGross).divide(totalQty, MONEY_SCALE, RoundingMode.HALF_UP));
  }

  private BigDecimal normalizeMoney(BigDecimal value) {
    if (value == null) {
      return zeroMoney();
    }
    return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal zeroMoney() {
    return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal zeroIfNull(BigDecimal value) {
    return value == null ? zeroMoney() : normalizeMoney(value);
  }

  private Order requireMatchedOrder(List<Order> makerOrders, Long makerOrderId) {
    return makerOrders.stream()
        .filter(order -> makerOrderId.equals(order.getId()))
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "matched maker order not found"));
  }

  private BusinessException noLiquidity(String symbol, String side, BigDecimal orderQty) {
    LinkedHashMap<String, Object> details = new LinkedHashMap<>();
    details.put("symbol", symbol);
    details.put("side", side);
    details.put("orderQty", normalizeMoney(orderQty));
    return new BusinessException(
        ErrorCode.ORD_NO_LIQUIDITY,
        ErrorCode.ORD_NO_LIQUIDITY.defaultMessage(),
        new ErrorMetadata("error.order.no_liquidity", "NO_LIQUIDITY"),
        details
    );
  }

  private BigDecimal resolveAvailableQuantity(Position position) {
    if (position.getQty() == null) {
      return BigDecimal.ZERO;
    }
    return position.getQty();
  }

  private String normalizeOrderType(String rawOrderType) {
    if (rawOrderType == null || rawOrderType.isBlank()) {
      return "LIMIT";
    }
    return rawOrderType.trim().toUpperCase(Locale.ROOT);
  }

  private BigDecimal resolveOrderPrice(String orderType, InternalOrderCreateCommand command) {
    return "MARKET".equals(orderType) ? null : command.getPrice();
  }

  private BigDecimal resolveReferencePrice(String orderType, InternalOrderCreateCommand command) {
    return "MARKET".equals(orderType) ? command.getPreTradePrice() : command.getPrice();
  }

  private String normalizeSide(String side) {
    if (side == null) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "side is required");
    }
    String normalized = side.trim().toUpperCase(Locale.ROOT);
    if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "side must be BUY or SELL");
    }
    return normalized;
  }

  private void ensureOrderEligibleAccountStatus(Account account) {
    AccountStatus accountStatus = parseAccountStatus(account.getStatus());

    if (!accountStatus.isOrderEligible()) {
      throw new BusinessException(
          ErrorCode.ORD_ACCOUNT_STATUS_BLOCKED,
          "account status " + accountStatus.name() + " is not eligible for order placement"
      );
    }
  }

  private AccountStatus parseAccountStatus(String rawStatus) {
    try {
      return AccountStatus.from(rawStatus);
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "unsupported account status: " + rawStatus, ex);
    }
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

  public record PendingOrderSubmission(
      Long orderId,
      Long accountId,
      String accountNo,
      String currency,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal orderQty,
      BigDecimal orderPrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      com.fix.common.fep.FepQuoteSourceMode quoteSourceMode,
      BigDecimal preTradePrice,
      String status
  ) {
    private static PendingOrderSubmission from(Order order, Account account) {
      return new PendingOrderSubmission(
          order.getId(),
          order.getAccountId(),
          account.getAccountNo(),
          account.getCurrency(),
          order.getClOrdId(),
          order.getSymbol(),
          order.getSide(),
          order.getOrderType(),
          order.getOrderQty(),
          order.getOrderPrice(),
          order.getQuoteSnapshotId(),
          order.getQuoteAsOf(),
          order.getQuoteSourceMode(),
          order.getPreTradePrice(),
          order.getStatus()
      );
      }
  }

  public record AccountOrderHistoryRow(
      String symbol,
      String side,
      BigDecimal orderQty,
      BigDecimal orderPrice,
      String status,
      String clOrdId,
      Instant createdAt
  ) {
  }

  public record AccountOrderHistoryPage(
      List<AccountOrderHistoryRow> content,
      long totalElements,
      int totalPages,
      int number,
      int size
  ) {
  }

  public record OrderSnapshot(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      String status,
      BigDecimal orderQty,
      BigDecimal orderPrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      com.fix.common.fep.FepQuoteSourceMode quoteSourceMode,
      BigDecimal preTradePrice,
      String externalSyncStatus,
      String fepReferenceId,
      String failureReason,
      Long version,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      Instant executedAt
  ) {
    private static OrderSnapshot from(Order order) {
      return new OrderSnapshot(
          order.getId(),
          order.getAccountId(),
          order.getClOrdId(),
          order.getSymbol(),
          order.getSide(),
          order.getOrderType(),
          order.getStatus(),
          order.getOrderQty(),
          order.getOrderPrice(),
          order.getQuoteSnapshotId(),
          order.getQuoteAsOf(),
          order.getQuoteSourceMode(),
          order.getPreTradePrice(),
          order.getExternalSyncStatus(),
          order.getFepReferenceId(),
          order.getFailureReason(),
          order.getVersion(),
          order.getExecutionResult(),
          order.getExecutedQty(),
          order.getLeavesQty(),
          order.getExecutedPrice(),
          order.getExecutedAt()
      );
    }
  }

  public record OrderStateUpdateResult(OrderSnapshot order, boolean updated) {
  }
}
