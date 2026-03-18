package com.fix.corebank.repository;

import com.fix.corebank.vo.LedgerIntegrityAnomaly;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerIntegrityQueryRepositoryImpl implements LedgerIntegrityQueryRepository {

  private static final String TYPE_NEGATIVE_POSITION = "NEGATIVE_POSITION";
  private static final String TYPE_ORPHAN_EXECUTION = "ORPHAN_EXECUTION";
  private static final String TYPE_JOURNAL_LEDGER_COUNT_MISMATCH = "JOURNAL_LEDGER_COUNT_MISMATCH";
  private static final String TYPE_JOURNAL_LEDGER_BALANCE_MISMATCH = "JOURNAL_LEDGER_BALANCE_MISMATCH";
  private static final String TYPE_MISSING_LEDGER_CL_ORD_REF = "MISSING_LEDGER_CL_ORD_REF";
  private static final String CL_ORD_REF_TYPE = "CL_ORD_ID";

  private final JdbcTemplate jdbcTemplate;

  public LedgerIntegrityQueryRepositoryImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<LedgerIntegrityAnomaly> findNegativePositions() {
    return jdbcTemplate.query(
        """
            SELECT p.id AS position_id,
                   p.account_id,
                   p.symbol,
                   p.qty
            FROM positions p
            WHERE p.qty < 0
            ORDER BY p.account_id, p.symbol, p.id
            """,
        (rs, rowNum) -> LedgerIntegrityAnomaly.of(
            TYPE_NEGATIVE_POSITION,
            "position quantity is negative: " + rs.getBigDecimal("qty").toPlainString(),
            rs.getLong("account_id"),
            rs.getString("symbol"),
            rs.getLong("position_id"),
            null,
            null,
            null,
            null,
            null
        )
    );
  }

  @Override
  public List<LedgerIntegrityAnomaly> findOrphanExecutions() {
    return jdbcTemplate.query(
        """
            SELECT e.id AS execution_id,
                   e.account_id,
                   e.symbol,
                   e.order_id,
                   e.cl_ord_id,
                   o.status AS order_status
            FROM executions e
            LEFT JOIN orders o
              ON o.id = e.order_id
            WHERE o.id IS NULL
               OR o.status <> 'FILLED'
            ORDER BY e.account_id, e.symbol, e.id
            """,
        (rs, rowNum) -> LedgerIntegrityAnomaly.of(
            TYPE_ORPHAN_EXECUTION,
            orphanExecutionMessage(rs.getString("order_status")),
            rs.getLong("account_id"),
            rs.getString("symbol"),
            null,
            rs.getLong("execution_id"),
            rs.getLong("order_id"),
            rs.getString("cl_ord_id"),
            null,
            null
        )
    );
  }

  @Override
  public List<LedgerIntegrityAnomaly> findJournalLedgerCountMismatches() {
    return jdbcTemplate.query(
        """
            SELECT j.id AS journal_entry_id,
                   j.order_id,
                   o.account_id,
                   o.symbol,
                   o.cl_ord_id,
                   COUNT(le.id) AS ledger_entry_count
            FROM journal_entries j
            LEFT JOIN ledger_entries le
              ON le.journal_entry_id = j.id
            LEFT JOIN orders o
              ON o.id = j.order_id
            GROUP BY j.id, j.order_id, o.account_id, o.symbol, o.cl_ord_id
            HAVING COUNT(le.id) <> 2
            ORDER BY j.id
            """,
        (rs, rowNum) -> LedgerIntegrityAnomaly.of(
            TYPE_JOURNAL_LEDGER_COUNT_MISMATCH,
            "journal entry must have exactly 2 ledger entries but found "
                + rs.getLong("ledger_entry_count"),
            nullableLong(rs, "account_id"),
            rs.getString("symbol"),
            null,
            null,
            nullableLong(rs, "order_id"),
            rs.getString("cl_ord_id"),
            rs.getLong("journal_entry_id"),
            null
        )
    );
  }

  @Override
  public List<LedgerIntegrityAnomaly> findJournalLedgerBalanceMismatches() {
    return jdbcTemplate.query(
        """
            SELECT j.id AS journal_entry_id,
                   j.order_id,
                   o.account_id,
                   o.symbol,
                   o.cl_ord_id,
                   COALESCE(SUM(CASE WHEN le.direction = 'DR' THEN le.amount ELSE 0 END), 0) AS debit_amount,
                   COALESCE(SUM(CASE WHEN le.direction = 'CR' THEN le.amount ELSE 0 END), 0) AS credit_amount
            FROM journal_entries j
            LEFT JOIN ledger_entries le
              ON le.journal_entry_id = j.id
            LEFT JOIN orders o
              ON o.id = j.order_id
            GROUP BY j.id, j.order_id, o.account_id, o.symbol, o.cl_ord_id
            HAVING COALESCE(SUM(CASE WHEN le.direction = 'DR' THEN le.amount ELSE 0 END), 0)
                 <> COALESCE(SUM(CASE WHEN le.direction = 'CR' THEN le.amount ELSE 0 END), 0)
            ORDER BY j.id
            """,
        (rs, rowNum) -> LedgerIntegrityAnomaly.of(
            TYPE_JOURNAL_LEDGER_BALANCE_MISMATCH,
            balanceMismatchMessage(rs.getBigDecimal("debit_amount"), rs.getBigDecimal("credit_amount")),
            nullableLong(rs, "account_id"),
            rs.getString("symbol"),
            null,
            null,
            nullableLong(rs, "order_id"),
            rs.getString("cl_ord_id"),
            rs.getLong("journal_entry_id"),
            null
        )
    );
  }

  @Override
  public List<LedgerIntegrityAnomaly> findMissingLedgerClOrdReferences() {
    return jdbcTemplate.query(
        """
            SELECT le.id AS ledger_entry_id,
                   le.account_id,
                   le.journal_entry_id,
                   j.order_id,
                   o.symbol,
                   o.cl_ord_id
            FROM ledger_entries le
            LEFT JOIN ledger_entry_refs ref
              ON ref.ledger_entry_id = le.id
             AND ref.ref_type = ?
            LEFT JOIN journal_entries j
              ON j.id = le.journal_entry_id
            LEFT JOIN orders o
              ON o.id = j.order_id
            WHERE ref.id IS NULL
            ORDER BY le.journal_entry_id, le.id
            """,
        missingLedgerClOrdReferenceMapper(),
        CL_ORD_REF_TYPE
    );
  }

  private RowMapper<LedgerIntegrityAnomaly> missingLedgerClOrdReferenceMapper() {
    return (rs, rowNum) -> LedgerIntegrityAnomaly.of(
        TYPE_MISSING_LEDGER_CL_ORD_REF,
        "ledger entry is missing CL_ORD_ID reference",
        rs.getLong("account_id"),
        rs.getString("symbol"),
        null,
        null,
        nullableLong(rs, "order_id"),
        rs.getString("cl_ord_id"),
        rs.getLong("journal_entry_id"),
        rs.getLong("ledger_entry_id")
    );
  }

  private String orphanExecutionMessage(String orderStatus) {
    if (orderStatus == null || orderStatus.isBlank()) {
      return "execution references missing order";
    }
    return "execution references non-FILLED order status: " + orderStatus;
  }

  private String balanceMismatchMessage(BigDecimal debitAmount, BigDecimal creditAmount) {
    return "journal entry debit/credit mismatch: debit="
        + toPlainString(debitAmount)
        + ", credit="
        + toPlainString(creditAmount);
  }

  private String toPlainString(BigDecimal amount) {
    return amount == null ? "0" : amount.toPlainString();
  }

  private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
    long value = rs.getLong(columnName);
    return rs.wasNull() ? null : value;
  }
}
