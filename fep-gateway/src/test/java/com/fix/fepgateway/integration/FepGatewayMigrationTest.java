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
    assertTableExists("gateway_sessions");
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
}
