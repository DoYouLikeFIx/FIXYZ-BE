package com.fix.fepgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FepGatewayMigrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

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
    assertIndexExists("gateway_orders", "uk_gateway_orders_reference_id");
    assertIndexExists("gateway_security_events", "idx_gateway_security_events_reference_id");
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
}
