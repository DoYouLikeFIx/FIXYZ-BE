package com.fix.corebank.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
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

import lombok.RequiredArgsConstructor;

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
    Account account = accountRepository.findByIdForUpdate(command.getAccountId())
        .or(() -> accountRepository.findById(command.getAccountId()))
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));
    ensureOrderEligibleAccountStatus(account);

    String side = normalizeSide(command.getSide());
    Position position = positionRepository.findByAccountIdAndSymbolForUpdate(command.getAccountId(), command.getSymbol())
        .orElseGet(() -> {
          Position createdPosition = Position.of(
              command.getAccountId(),
              command.getSymbol(),
              BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
              BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
          );
          Position persistedPosition = positionRepository.saveAndFlush(createdPosition);
          return persistedPosition != null ? persistedPosition : createdPosition;
        });
    BigDecimal availableQty = resolveAvailableQuantity(position);

    BigDecimal todaySellQty = executionRepository.sumSellQuantityByAccountAndSymbolBetween(
        command.getAccountId(),
        command.getSymbol(),
        startOfLimitWindowDay(),
        startOfNextLimitWindowDay()
    );

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

    Order candidateOrder = Order.accepted(
        command.getAccountId(),
        command.getClOrdId(),
        command.getSymbol(),
        side,
        command.getQuantity(),
        command.getPrice()
    );
    Order savedOrder = orderRepository.saveAndFlush(candidateOrder);
    if (savedOrder == null) {
      savedOrder = candidateOrder;
    }

    Instant executedAt = Instant.now();
    BigDecimal executedQty = normalizeMoney(command.getQuantity());
    BigDecimal executedPrice = normalizeMoney(command.getPrice());
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

  private BigDecimal normalizeMoney(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
    return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal resolveAvailableQuantity(Position position) {
    if (position.getQty() == null) {
      return BigDecimal.ZERO;
    }
    return position.getQty();
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
      BigDecimal orderQty,
      BigDecimal orderPrice,
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
          order.getOrderQty(),
          order.getOrderPrice(),
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
      String clOrdId,
      String status,
      BigDecimal orderQty,
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
          order.getClOrdId(),
          order.getStatus(),
          order.getOrderQty(),
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
