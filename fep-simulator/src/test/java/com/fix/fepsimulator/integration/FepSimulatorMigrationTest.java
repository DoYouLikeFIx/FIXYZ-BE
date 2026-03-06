package com.fix.fepsimulator.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FepSimulatorMigrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldCreateSimulatorScaffoldingTables() {
    assertTableExists("simulator_connections");
    assertTableExists("simulator_messages");
    assertTableExists("simulator_rules");
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
