package com.fix.corebank.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class CoreFlywayExecutionSequenceUpgradeTest {

  private static final String URL = "jdbc:h2:mem:core_migration_upgrade_seq;MODE=MySQL;DB_CLOSE_DELAY=-1";
  private static final String USERNAME = "sa";
  private static final String PASSWORD = "";

  @Test
  void shouldBackfillExecutionSequenceForLegacyRowsDuringUpgrade() throws Exception {
    Flyway.configure()
        .dataSource(URL, USERNAME, PASSWORD)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("12"))
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
      insertLegacyOrder(connection, 100L, "legacy-order-100");
      insertLegacyExecution(connection, 1L, 100L, "legacy-order-100", "2026-03-01 10:00:00");
      insertLegacyExecution(connection, 2L, 100L, "legacy-order-100", "2026-03-01 10:00:05");
    }

    Flyway.configure()
        .dataSource(URL, USERNAME, PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
      assertThat(readExecutionSequences(connection, 100L)).containsExactly(1, 2);
      assertThatThrownBy(() -> insertDuplicateExecutionSequence(connection))
          .isInstanceOf(Exception.class);
    }
  }

  private void insertLegacyOrder(Connection connection, long orderId, String clOrdId) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(
        "INSERT INTO orders "
            + "(id, account_id, cl_ord_id, symbol, side, order_qty, order_type, order_price, status, requested_at, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TIMESTAMP '2026-03-01 09:59:00', TIMESTAMP '2026-03-01 09:59:00', TIMESTAMP '2026-03-01 09:59:00', ?)"
    )) {
      statement.setLong(1, orderId);
      statement.setLong(2, 1L);
      statement.setString(3, clOrdId);
      statement.setString(4, "005930");
      statement.setString(5, "BUY");
      statement.setBigDecimal(6, new java.math.BigDecimal("3.0000"));
      statement.setString(7, "LIMIT");
      statement.setBigDecimal(8, new java.math.BigDecimal("70000.0000"));
      statement.setString(9, "PARTIALLY_FILLED");
      statement.setLong(10, 0L);
      statement.executeUpdate();
    }
  }

  private void insertLegacyExecution(
      Connection connection,
      long executionId,
      long orderId,
      String clOrdId,
      String executedAt
  ) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(
        "INSERT INTO executions "
            + "(id, order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, executed_at, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    )) {
      statement.setLong(1, executionId);
      statement.setLong(2, orderId);
      statement.setLong(3, 1L);
      statement.setString(4, clOrdId);
      statement.setString(5, "005930");
      statement.setString(6, "BUY");
      statement.setBigDecimal(7, new java.math.BigDecimal("1.5000"));
      statement.setBigDecimal(8, new java.math.BigDecimal("70000.0000"));
      Timestamp timestamp = Timestamp.valueOf(executedAt.replace("T", " ").replace("Z", ""));
      statement.setTimestamp(9, timestamp);
      statement.setTimestamp(10, timestamp);
      statement.setTimestamp(11, timestamp);
      statement.setLong(12, 0L);
      statement.executeUpdate();
    }
  }

  private List<Integer> readExecutionSequences(Connection connection, long orderId) throws Exception {
    List<Integer> executionSequences = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT execution_seq FROM executions WHERE order_id = ? ORDER BY executed_at, id"
    )) {
      statement.setLong(1, orderId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          executionSequences.add(resultSet.getInt(1));
        }
      }
    }
    return executionSequences;
  }

  private void insertDuplicateExecutionSequence(Connection connection) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(
        "INSERT INTO executions "
            + "(id, order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, execution_seq, executed_at, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TIMESTAMP '2026-03-01 10:00:10', TIMESTAMP '2026-03-01 10:00:10', TIMESTAMP '2026-03-01 10:00:10', ?)"
    )) {
      statement.setLong(1, 3L);
      statement.setLong(2, 100L);
      statement.setLong(3, 1L);
      statement.setString(4, "legacy-order-100");
      statement.setString(5, "005930");
      statement.setString(6, "BUY");
      statement.setBigDecimal(7, new java.math.BigDecimal("1.0000"));
      statement.setBigDecimal(8, new java.math.BigDecimal("70100.0000"));
      statement.setInt(9, 1);
      statement.setLong(10, 0L);
      statement.executeUpdate();
    }
  }
}
