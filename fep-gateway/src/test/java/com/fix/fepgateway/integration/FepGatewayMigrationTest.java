package com.fix.fepgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FepGatewayMigrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private DataSource dataSource;

  @Test
  void shouldCreateGatewayScaffoldingTables() {
    assertTableExists("gateway_orders");
    assertTableExists("gateway_order_cancels");
    assertTableExists("gateway_order_replays");
    assertTableExists("gateway_security_events");
    assertTableExists("gateway_sessions");
    assertColumnExists("gateway_orders", "account_id");
    assertColumnExists("gateway_orders", "reference_id");
    assertColumnExists("gateway_orders", "reference_id_expires_at");
    assertColumnExists("gateway_orders", "status_message");
    assertColumnExists("gateway_orders", "reject_reason");
    assertColumnExists("gateway_orders", "parse_error");
    assertVarcharColumnContract("gateway_orders", "account_id", false, 64);
    assertVarcharColumnContract("gateway_orders", "reference_id", false, 128);
    assertVarcharColumnContract("gateway_orders", "status_message", true, 255);
    assertVarcharColumnContract("gateway_orders", "reject_reason", true, 64);
    assertVarcharColumnContract("gateway_orders", "parse_error", true, 255);
    assertColumnNullability("gateway_orders", "reference_id_expires_at", false);
    assertVarcharColumnContract("gateway_security_events", "correlation_id", false, 64);
    assertIndexExists("gateway_orders", "uk_gateway_orders_reference_id");
    assertIndexExists("gateway_security_events", "idx_gateway_security_events_reference_id");
  }

  @Test
  void shouldBackfillLegacyStatusDiagnosticsDeterministically() {
    insertLegacyGatewayOrder("123e4567-e89b-42d3-a456-426614174041", "UNKNOWN");
    insertLegacyGatewayOrder("123e4567-e89b-42d3-a456-426614174042", "PENDING");
    insertLegacyGatewayOrder("123e4567-e89b-42d3-a456-426614174043", "REJECTED");
    insertLegacyGatewayOrder("123e4567-e89b-42d3-a456-426614174044", "MALFORMED");

    new ResourceDatabasePopulator(
        new ClassPathResource("db/migration/V9__backfill_gateway_status_diagnostics.sql")
    ).execute(dataSource);

    assertStatusDiagnostics(
        "123e4567-e89b-42d3-a456-426614174041",
        "execution state is unresolved in external system",
        null,
        null
    );
    assertStatusDiagnostics(
        "123e4567-e89b-42d3-a456-426614174042",
        "execution report is still pending",
        null,
        null
    );
    assertStatusDiagnostics(
        "123e4567-e89b-42d3-a456-426614174043",
        null,
        "OTHER",
        null
    );
    assertStatusDiagnostics(
        "123e4567-e89b-42d3-a456-426614174044",
        "FIX ExecutionReport parse failed; manual review required",
        null,
        "PARSE_ERROR:LEGACY_STATUS_ROW"
    );
  }

  private void assertTableExists(String tableName) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?",
        Integer.class,
        tableName.toUpperCase()
    );
    assertThat(count).isNotNull();
    assertThat(count).isGreaterThan(0);
  }

  private void assertColumnExists(String tableName, String columnName) {
    Integer count = jdbcTemplate.queryForObject(
        """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """,
        Integer.class,
        tableName.toUpperCase(),
        columnName.toUpperCase()
    );
    assertThat(count).isNotNull();
    assertThat(count).isGreaterThan(0);
  }

  private void assertIndexExists(String tableName, String indexName) {
    Integer count = jdbcTemplate.queryForObject(
        """
            SELECT COUNT(*)
            FROM (
              SELECT INDEX_NAME AS OBJECT_NAME
              FROM INFORMATION_SCHEMA.INDEXES
              WHERE TABLE_NAME = ?
              UNION ALL
              SELECT CONSTRAINT_NAME AS OBJECT_NAME
              FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
              WHERE TABLE_NAME = ?
            ) named_objects
            WHERE OBJECT_NAME = ?
            """,
        Integer.class,
        tableName.toUpperCase(),
        tableName.toUpperCase(),
        indexName.toUpperCase()
    );
    assertThat(count).isNotNull();
    assertThat(count).isGreaterThan(0);
  }

  private void assertVarcharColumnContract(String tableName, String columnName, boolean nullable, int maxLength) {
    Map<String, Object> row = jdbcTemplate.queryForMap(
        """
            SELECT IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """,
        tableName.toUpperCase(),
        columnName.toUpperCase()
    );
    assertThat(row.get("IS_NULLABLE")).isEqualTo(nullable ? "YES" : "NO");
    assertThat(((Number) row.get("CHARACTER_MAXIMUM_LENGTH")).intValue()).isEqualTo(maxLength);
  }

  private void assertColumnNullability(String tableName, String columnName, boolean nullable) {
    String isNullable = jdbcTemplate.queryForObject(
        """
            SELECT IS_NULLABLE
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """,
        String.class,
        tableName.toUpperCase(),
        columnName.toUpperCase()
    );
    assertThat(isNullable).isEqualTo(nullable ? "YES" : "NO");
  }

  private void insertLegacyGatewayOrder(String clOrdId, String status) {
    Timestamp now = Timestamp.from(Instant.parse("2026-03-01T10:00:00Z"));
    jdbcTemplate.update(
        """
            INSERT INTO gateway_orders (
              cl_ord_id,
              account_id,
              reference_id,
              reference_id_expires_at,
              symbol,
              side,
              qty,
              order_type,
              requested_price,
              status,
              fep_order_id,
              exec_type,
              executed_qty,
              executed_price,
              leaves_qty,
              transact_time,
              status_message,
              reject_reason,
              parse_error,
              transport,
              recovery_status,
              cancel_failure_mode,
              requery_ord_status,
              requery_executed_qty,
              requery_executed_price,
              created_at,
              updated_at,
              version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        clOrdId,
        "LEGACY",
        "LEGACY-" + clOrdId,
        now,
        "005930",
        "BUY",
        new BigDecimal("10.0000"),
        "LIMIT",
        72000L,
        status,
        null,
        null,
        0L,
        null,
        10L,
        null,
        null,
        null,
        null,
        "FIX",
        "ACTIVE",
        "NONE",
        null,
        null,
        null,
        now,
        now,
        0L
    );
  }

  private void assertStatusDiagnostics(String clOrdId, String message, String rejectReason, String parseError) {
    Map<String, Object> row = jdbcTemplate.queryForMap(
        """
            SELECT status_message, reject_reason, parse_error
            FROM gateway_orders
            WHERE cl_ord_id = ?
            """,
        clOrdId
    );
    assertThat(row.get("status_message")).isEqualTo(message);
    assertThat(row.get("reject_reason")).isEqualTo(rejectReason);
    assertThat(row.get("parse_error")).isEqualTo(parseError);
  }
}
