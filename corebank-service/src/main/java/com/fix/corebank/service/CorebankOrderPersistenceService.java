package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.entity.Account;
import com.fix.corebank.entity.JournalEntry;
import com.fix.corebank.entity.LedgerEntry;
import com.fix.corebank.entity.LedgerEntryRef;
import com.fix.corebank.entity.Order;
import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.JournalEntryRepository;
import com.fix.corebank.repository.LedgerEntryRefRepository;
import com.fix.corebank.repository.LedgerEntryRepository;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CorebankOrderPersistenceService {

  private final AccountRepository accountRepository;
  private final OrderRepository orderRepository;
  private final PositionRepository positionRepository;
  private final ExecutionRepository executionRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final LedgerEntryRefRepository ledgerEntryRefRepository;

  @Transactional(readOnly = true)
  public Optional<OrderSnapshot> findOrder(String clOrdId) {
    return orderRepository.findByClOrdId(clOrdId).map(OrderSnapshot::from);
  }

  @Transactional(readOnly = true)
  public OrderSnapshot getRequiredOrder(String clOrdId) {
    return findOrder(clOrdId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "order not found"));
  }

  @Transactional
  public PendingOrderSubmission prepareOrderSubmission(InternalOrderCreateCommand command) {
    Account account = accountRepository.findById(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    String side = normalizeSide(command.getSide());
    positionRepository.findByAccountIdAndSymbolForUpdate(command.getAccountId(), command.getSymbol())
        .orElseGet(() -> positionRepository.saveAndFlush(
            Position.of(command.getAccountId(), command.getSymbol(), BigDecimal.ZERO, BigDecimal.ZERO)
        ));

    BigDecimal todaySellQty = executionRepository.sumSellQuantityByAccountAndSymbolBetween(
        command.getAccountId(),
        command.getSymbol(),
        startOfUtcDay(),
        startOfNextUtcDay()
    );

    if ("SELL".equals(side)) {
      BigDecimal afterSell = todaySellQty.add(command.getQuantity());
      if (afterSell.compareTo(account.getDailySellLimit()) > 0) {
        throw new BusinessException(
            ErrorCode.ORD_INVALID_REQUEST,
            "daily sell limit exceeded for account " + account.getAccountNo()
        );
      }
    }

    Order savedOrder = orderRepository.saveAndFlush(Order.accepted(
        command.getAccountId(),
        command.getClOrdId(),
        command.getSymbol(),
        side,
        command.getQuantity(),
        command.getPrice()
    ));
    appendLedgerSkeleton(savedOrder);
    return PendingOrderSubmission.from(savedOrder, account);
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

  private void appendLedgerSkeleton(Order order) {
    BigDecimal grossAmount = order.getOrderPrice().multiply(order.getOrderQty());

    JournalEntry journalEntry = journalEntryRepository.save(
        JournalEntry.of(order.getId(), "ORDER_ACCEPTED", grossAmount, "corebank scaffold journal")
    );
    LedgerEntry ledgerEntry = ledgerEntryRepository.save(
        LedgerEntry.of(journalEntry.getId(), order.getAccountId(), "ORDER", "DR", grossAmount)
    );
    ledgerEntryRefRepository.save(LedgerEntryRef.of(ledgerEntry.getId(), "CL_ORD_ID", order.getClOrdId()));
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

  private Instant startOfUtcDay() {
    return LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  private Instant startOfNextUtcDay() {
    return LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
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

  public record OrderSnapshot(
      Long orderId,
      String clOrdId,
      String status,
      BigDecimal orderQty,
      String externalSyncStatus,
      String fepReferenceId,
      String failureReason
  ) {
    private static OrderSnapshot from(Order order) {
      return new OrderSnapshot(
          order.getId(),
          order.getClOrdId(),
          order.getStatus(),
          order.getOrderQty(),
          order.getExternalSyncStatus(),
          order.getFepReferenceId(),
          order.getFailureReason()
      );
    }
  }
}
