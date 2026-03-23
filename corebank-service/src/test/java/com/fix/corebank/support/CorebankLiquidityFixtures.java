package com.fix.corebank.support;

import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CorebankLiquidityFixtures {

  private static final BigDecimal DEFAULT_CASH_BALANCE =
      new BigDecimal("1000000000.0000").setScale(4, RoundingMode.HALF_UP);
  private static final BigDecimal DEFAULT_DAILY_SELL_LIMIT =
      new BigDecimal("1000000.0000").setScale(4, RoundingMode.HALF_UP);

  private CorebankLiquidityFixtures() {
  }

  public static void seedRestingSellLiquidity(
      JdbcTemplate jdbcTemplate,
      OrderRepository orderRepository,
      Long accountId,
      Long memberId,
      String accountNo,
      String symbol,
      String clOrdId,
      BigDecimal quantity,
      BigDecimal price
  ) {
    ensureMember(jdbcTemplate, memberId);
    ensureAccount(jdbcTemplate, accountId, memberId, accountNo);
    ensurePosition(jdbcTemplate, accountId, symbol, quantity, price);
    orderRepository.saveAndFlush(Order.accepted(
        accountId,
        clOrdId,
        symbol,
        "SELL",
        quantity,
        price
    ));
  }

  public static void seedRestingBuyLiquidity(
      JdbcTemplate jdbcTemplate,
      OrderRepository orderRepository,
      Long accountId,
      Long memberId,
      String accountNo,
      String symbol,
      String clOrdId,
      BigDecimal quantity,
      BigDecimal price
  ) {
    ensureMember(jdbcTemplate, memberId);
    ensureAccount(jdbcTemplate, accountId, memberId, accountNo);
    orderRepository.saveAndFlush(Order.accepted(
        accountId,
        clOrdId,
        symbol,
        "BUY",
        quantity,
        price
    ));
  }

  private static void ensureMember(JdbcTemplate jdbcTemplate, Long memberId) {
    Integer existingCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM member WHERE id = ?",
        Integer.class,
        memberId
    );
    if (existingCount != null && existingCount > 0) {
      return;
    }
    jdbcTemplate.update(
        """
            INSERT INTO member (id, member_no, email, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """,
        memberId,
        "M-%05d".formatted(memberId),
        "member-%d@fix.test".formatted(memberId)
    );
  }

  private static void ensureAccount(
      JdbcTemplate jdbcTemplate,
      Long accountId,
      Long memberId,
      String accountNo
  ) {
    Integer existingCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM accounts WHERE id = ?",
        Integer.class,
        accountId
    );
    if (existingCount != null && existingCount > 0) {
      jdbcTemplate.update(
          """
              UPDATE accounts
                 SET member_id = ?,
                     status = 'ACTIVE',
                     currency = 'KRW',
                     cash_balance = ?,
                     daily_sell_limit = ?
               WHERE id = ?
              """,
          memberId,
          DEFAULT_CASH_BALANCE,
          DEFAULT_DAILY_SELL_LIMIT,
          accountId
      );
      return;
    }

    jdbcTemplate.update(
        """
            INSERT INTO accounts (
              id,
              account_no,
              currency,
              cash_balance,
              daily_sell_limit,
              member_id,
              status,
              created_at,
              updated_at,
              version
            )
            VALUES (?, ?, 'KRW', ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
        accountId,
        accountNo,
        DEFAULT_CASH_BALANCE,
        DEFAULT_DAILY_SELL_LIMIT,
        memberId
    );
  }

  private static void ensurePosition(
      JdbcTemplate jdbcTemplate,
      Long accountId,
      String symbol,
      BigDecimal quantity,
      BigDecimal avgPrice
  ) {
    Integer existingCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM positions WHERE account_id = ? AND symbol = ?",
        Integer.class,
        accountId,
        symbol
    );
    if (existingCount != null && existingCount > 0) {
      jdbcTemplate.update(
          """
              UPDATE positions
                 SET qty = ?,
                     avg_price = ?,
                     updated_at = CURRENT_TIMESTAMP
               WHERE account_id = ? AND symbol = ?
              """,
          quantity.setScale(4, RoundingMode.HALF_UP),
          avgPrice.setScale(4, RoundingMode.HALF_UP),
          accountId,
          symbol
      );
      return;
    }

    jdbcTemplate.update(
        """
            INSERT INTO positions (account_id, symbol, qty, avg_price, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
        accountId,
        symbol,
        quantity.setScale(4, RoundingMode.HALF_UP),
        avgPrice.setScale(4, RoundingMode.HALF_UP)
    );
  }
}
