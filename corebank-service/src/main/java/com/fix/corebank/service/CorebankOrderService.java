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
import com.fix.corebank.vo.InternalOrderRequeryCommand;
import com.fix.corebank.vo.InternalOrderResult;
import com.fix.corebank.vo.PortfolioQueryCommand;
import com.fix.corebank.vo.PortfolioResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CorebankOrderService {

  private final AccountRepository accountRepository;
  private final OrderRepository orderRepository;
  private final PositionRepository positionRepository;
  private final ExecutionRepository executionRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final LedgerEntryRefRepository ledgerEntryRefRepository;

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

  @Transactional
  public InternalOrderResult createOrder(InternalOrderCreateCommand command) {
    return orderRepository.findByClOrdId(command.getClOrdId())
        .map(existing -> mapToOrderResult(existing, true))
        .orElseGet(() -> createFreshOrder(command));
  }

  @Transactional(readOnly = true)
  public InternalOrderResult requeryOrder(InternalOrderRequeryCommand command) {
    Order order = orderRepository.findByClOrdId(command.getClOrdId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "order not found"));
    return mapToOrderResult(order, true);
  }

  private InternalOrderResult createFreshOrder(InternalOrderCreateCommand command) {
    Account account = accountRepository.findById(command.getAccountId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    String side = normalizeSide(command.getSide());
    Position lockedPosition = positionRepository.findByAccountIdAndSymbolForUpdate(command.getAccountId(), command.getSymbol())
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

    Order order = Order.accepted(
        command.getAccountId(),
        command.getClOrdId(),
        command.getSymbol(),
        side,
        command.getQuantity(),
        command.getPrice()
    );

    try {
      Order saved = orderRepository.saveAndFlush(order);
      appendLedgerSkeleton(saved);
      return mapToOrderResult(saved, false);
    } catch (DataIntegrityViolationException e) {
      return orderRepository.findByClOrdId(command.getClOrdId())
          .map(existing -> mapToOrderResult(existing, true))
          .orElseThrow(() -> e);
    }
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

  private InternalOrderResult mapToOrderResult(Order order, boolean idempotent) {
    return InternalOrderResult.of(
        order.getId(),
        order.getClOrdId(),
        order.getStatus(),
        idempotent,
        order.getOrderQty()
    );
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
}
