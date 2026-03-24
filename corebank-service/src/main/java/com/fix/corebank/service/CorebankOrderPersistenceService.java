package com.fix.corebank.service;

import com.fix.common.fep.FepOrderType;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
  private final CorebankMatchingEngine matchingEngine;
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
    List<Order> makerOrders = Optional.ofNullable(oppositeBookQueryService.lockExecutionCandidates(command.getSymbol(), side))
        .orElse(List.of());
    CorebankMatchingEngine.MatchResult matchResult = matchingEngine.match(
        toMatchRequest(command, side, orderType, makerOrders)
    );
    if (matchResult.rejected()) {
      throw noLiquidity(command.getSymbol(), side, command.getQuantity());
    }

    if (matchResult.resting()) {
      return prepareRestingLimitOrderSubmission(command, side, orderType);
    }

    return prepareMatchedOrderSubmission(command, side, orderType, matchResult, makerOrders);
  }

  private PendingOrderSubmission prepareRestingLimitOrderSubmission(
      InternalOrderCreateCommand command,
      String side,
      String orderType
  ) {
    long waitStartedAtNanos = System.nanoTime();
    Position position;
    try {
      position = lockPositionForUpdate(command.getAccountId(), command.getSymbol());
    } finally {
      positionLockMetrics.recordWait(waitStartedAtNanos);
    }
    positionLockMetrics.recordHoldOnTransactionCompletion(System.nanoTime());
    orderPreparationLockHook.afterPositionLock(command.getAccountId(), command.getSymbol());

    Account account = accountRepository.findByIdForUpdate(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));
    ensureOrderEligibleAccountStatus(account);
    validateSellConstraints(command, side, account, position);

    Order candidateOrder = Order.accepted(
        command.getAccountId(),
        command.getClOrdId(),
        command.getSymbol(),
        side,
        orderType,
        command.getQuantity(),
        resolveOrderPrice(orderType, command),
        command.getPreTradePrice(),
        command.getQuoteSnapshotId(),
        command.getQuoteAsOf(),
        command.getQuoteSourceMode()
    );
    candidateOrder.markResting(normalizeMoney(command.getQuantity()));

    Order savedOrder = orderRepository.saveAndFlush(candidateOrder);
    if (savedOrder == null) {
      savedOrder = candidateOrder;
    }
    return PendingOrderSubmission.from(savedOrder, account);
  }

  private PendingOrderSubmission prepareMatchedOrderSubmission(
      InternalOrderCreateCommand command,
      String side,
      String orderType,
      CorebankMatchingEngine.MatchResult matchResult,
      List<Order> makerOrders
  ) {
    MarketParticipantLocks participantLocks = lockMarketParticipants(command, matchResult, makerOrders);
    Account takerAccount = participantLocks.requireAccount(command.getAccountId());
    Position takerPosition = participantLocks.requirePosition(command.getAccountId(), command.getSymbol());
    orderPreparationLockHook.afterPositionLock(command.getAccountId(), command.getSymbol());
    ensureOrderEligibleAccountStatus(takerAccount);
    validateSellConstraints(command, side, takerAccount, takerPosition);

    Order takerOrder = orderRepository.saveAndFlush(Order.accepted(
        command.getAccountId(),
        command.getClOrdId(),
        command.getSymbol(),
        side,
        orderType,
        command.getQuantity(),
        resolveOrderPrice(orderType, command),
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
    Map<Long, Integer> nextExecutionSequences = new LinkedHashMap<>();
    BigDecimal takerExecutedQty = zeroMoney();
    BigDecimal takerExecutedPrice = null;
    for (CorebankMatchingEngine.MatchFill fill : matchResult.fills()) {
      Order makerOrder = requireMatchedOrder(makerOrders, fill.makerOrderId());
      Account makerAccount = participantLocks.requireAccount(makerOrder.getAccountId());
      Position makerPosition = participantLocks.requirePosition(makerOrder.getAccountId(), makerOrder.getSymbol());
      BigDecimal fillQty = normalizeMoney(fill.executedQty());
      BigDecimal fillPrice = normalizeMoney(fill.executedPrice());
      BigDecimal fillGross = calculateGrossAmount(fillQty, fillPrice);

      applyCanonicalPosting(takerAccount, takerPosition, side, fillQty, fillPrice, fillGross);
      applyCanonicalPosting(makerAccount, makerPosition, makerOrder.getSide(), fillQty, fillPrice, fillGross);

      saveExecutionFill(
          takerOrder,
          fillQty,
          fillPrice,
          nextExecutionSequence(takerOrder, nextExecutionSequences),
          executedAt
      );
      saveExecutionFill(
          makerOrder,
          fillQty,
          fillPrice,
          nextExecutionSequence(makerOrder, nextExecutionSequences),
          executedAt
      );

      applyMatchSummary(makerOrder, fillQty, fillPrice, executedAt);
      orderPostingTransactionHook.afterPostingMutation(makerOrder, makerAccount, makerPosition);
      appendExecutionPosting(makerOrder, fillGross);
      totalGross = totalGross.add(fillGross).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      takerExecutedPrice = weightedAveragePrice(takerExecutedQty, takerExecutedPrice, fillQty, fillPrice);
      takerExecutedQty = takerExecutedQty.add(fillQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    applyCanonicalExecutionSummary(takerOrder, takerExecutedQty, takerExecutedPrice, executedAt);
    orderPostingTransactionHook.afterPostingMutation(takerOrder, takerAccount, takerPosition);
    appendExecutionPosting(takerOrder, totalGross);
    orderRepository.flush();
    return PendingOrderSubmission.from(takerOrder, takerAccount);
  }

  private CorebankMatchingEngine.MatchRequest toMatchRequest(
      InternalOrderCreateCommand command,
      String side,
      String orderType,
      List<Order> makerOrders
  ) {
    List<CorebankMatchingEngine.MatchBookEntry> oppositeBook = makerOrders.stream()
        .map(oppositeBookQueryService::toEntry)
        .map(entry -> new CorebankMatchingEngine.MatchBookEntry(
            entry.orderId(),
            entry.accountId(),
            entry.clOrdId(),
            entry.symbol(),
            entry.side(),
            entry.remainingQty(),
            entry.limitPrice(),
            entry.priorityTime(),
            entry.status()
        ))
        .toList();
    if (FepOrderType.MARKET.name().equals(orderType)) {
      return CorebankMatchingEngine.MatchRequest.market(command.getQuantity(), oppositeBook);
    }
    return CorebankMatchingEngine.MatchRequest.limit(side, command.getQuantity(), command.getPrice(), oppositeBook);
  }

  private void validateSellConstraints(
      InternalOrderCreateCommand command,
      String side,
      Account account,
      Position position
  ) {
    if (!"SELL".equals(side)) {
      return;
    }

    BigDecimal availableQty = resolveAvailableQuantity(position);
    if (command.getQuantity().compareTo(availableQty) > 0) {
      throw new InsufficientPositionException(
          command.getAccountId(),
          command.getSymbol(),
          availableQty,
          command.getQuantity()
      );
    }

    BigDecimal todaySellQty = executionRepository.sumSellQuantityByAccountAndSymbolBetween(
        command.getAccountId(),
        command.getSymbol(),
        startOfLimitWindowDay(),
        startOfNextLimitWindowDay()
    );
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

  private MarketParticipantLocks lockMarketParticipants(
      InternalOrderCreateCommand command,
      CorebankMatchingEngine.MatchResult matchResult,
      List<Order> makerOrders
  ) {
    // Keep the acquisition order stable across executions:
    // matched book rows are locked before entering this method,
    // then participant accounts,
    // then participant positions ordered by symbol/account key.
    long waitStartedAtNanos = System.nanoTime();
    Map<Long, Account> lockedAccounts = new LinkedHashMap<>();
    Map<ParticipantPositionKey, Position> lockedPositions = new LinkedHashMap<>();
    try {
      List<Long> accountIds = java.util.stream.Stream.concat(
              java.util.stream.Stream.of(command.getAccountId()),
              matchResult.fills().stream().map(CorebankMatchingEngine.MatchFill::makerAccountId)
          )
          .distinct()
          .sorted()
          .toList();
      for (Long accountId : accountIds) {
        Account account = accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));
        lockedAccounts.put(accountId, account);
      }

      List<ParticipantPositionKey> positionKeys = java.util.stream.Stream.concat(
              java.util.stream.Stream.of(new ParticipantPositionKey(command.getAccountId(), command.getSymbol())),
              matchResult.fills().stream()
                  .map(fill -> requireMatchedOrder(makerOrders, fill.makerOrderId()))
                  .map(order -> new ParticipantPositionKey(order.getAccountId(), order.getSymbol()))
          )
          .distinct()
          .sorted(Comparator
              .comparing(ParticipantPositionKey::symbol)
              .thenComparing(ParticipantPositionKey::accountId))
          .toList();
      for (ParticipantPositionKey positionKey : positionKeys) {
        lockedPositions.put(
            positionKey,
            lockPositionForUpdate(positionKey.accountId(), positionKey.symbol())
        );
      }
    } finally {
      positionLockMetrics.recordWait(waitStartedAtNanos);
    }
    positionLockMetrics.recordHoldOnTransactionCompletion(System.nanoTime());
    return new MarketParticipantLocks(lockedAccounts, lockedPositions);
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

  private void saveExecutionFill(
      Order order,
      BigDecimal executedQty,
      BigDecimal executedPrice,
      int executionSeq,
      Instant executedAt
  ) {
    executionRepository.saveAndFlush(Execution.of(
        order.getId(),
        order.getAccountId(),
        order.getClOrdId(),
        order.getSymbol(),
        order.getSide(),
        executedQty,
        executedPrice,
        executionSeq,
        order.getQuoteSnapshotId(),
        order.getQuoteAsOf(),
        order.getQuoteSourceMode(),
        executedAt
    ));
  }

  private int nextExecutionSequence(Order order, Map<Long, Integer> nextExecutionSequences) {
    return nextExecutionSequences.compute(order.getId(), (orderId, nextExecutionSeq) -> {
      if (nextExecutionSeq == null) {
        long persistedCount = executionRepository.countByOrderId(orderId);
        return Math.toIntExact(persistedCount) + 1;
      }
      return nextExecutionSeq + 1;
    });
  }

  private void applyMatchSummary(
      Order order,
      BigDecimal fillQty,
      BigDecimal fillPrice,
      Instant executedAt
  ) {
    BigDecimal currentExecutedQty = zeroIfNull(order.getExecutedQty());
    BigDecimal nextExecutedQty = currentExecutedQty.add(fillQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal nextExecutedPrice = weightedAveragePrice(currentExecutedQty, order.getExecutedPrice(), fillQty, fillPrice);
    applyCanonicalExecutionSummary(order, nextExecutedQty, nextExecutedPrice, executedAt);
  }

  private void applyCanonicalExecutionSummary(
      Order order,
      BigDecimal executedQty,
      BigDecimal executedPrice,
      Instant executedAt
  ) {
    CanonicalExecutionSummary summary = canonicalExecutionSummary(order, executedQty, executedPrice, executedAt);
    order.completeExecution(
        summary.status(),
        summary.executionResult(),
        summary.executedQty(),
        summary.leavesQty(),
        summary.executedPrice(),
        summary.executedAt()
    );
  }

  private CanonicalExecutionSummary canonicalExecutionSummary(
      Order order,
      BigDecimal executedQty,
      BigDecimal executedPrice,
      Instant executedAt
  ) {
    BigDecimal normalizedExecutedQty = normalizeMoney(executedQty);
    BigDecimal normalizedLeavesQty = normalizeMoney(order.getOrderQty().subtract(normalizedExecutedQty));
    if (normalizedLeavesQty.signum() < 0) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "executed quantity exceeds order quantity");
    }
    String status = normalizedLeavesQty.signum() == 0 ? "FILLED" : "PARTIALLY_FILLED";
    return new CanonicalExecutionSummary(
        status,
        status,
        normalizedExecutedQty,
        normalizedLeavesQty,
        normalizeMoney(executedPrice),
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
      return FepOrderType.LIMIT.name();
    }
    String normalized = rawOrderType.trim().toUpperCase(Locale.ROOT);
    if (!FepOrderType.LIMIT.name().equals(normalized) && !FepOrderType.MARKET.name().equals(normalized)) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "orderType must be LIMIT or MARKET");
    }
    return normalized;
  }

  private BigDecimal resolveOrderPrice(String orderType, InternalOrderCreateCommand command) {
    return "MARKET".equals(orderType) ? null : command.getPrice();
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

  private record ParticipantPositionKey(Long accountId, String symbol) {
  }

  private record CanonicalExecutionSummary(
      String status,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      Instant executedAt
  ) {
  }

  private record MarketParticipantLocks(
      Map<Long, Account> accounts,
      Map<ParticipantPositionKey, Position> positions
  ) {
    private Account requireAccount(Long accountId) {
      Account account = accounts.get(accountId);
      if (account == null) {
        throw new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found");
      }
      return account;
    }

    private Position requirePosition(Long accountId, String symbol) {
      Position position = positions.get(new ParticipantPositionKey(accountId, symbol));
      if (position == null) {
        throw new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "position not found");
      }
      return position;
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
