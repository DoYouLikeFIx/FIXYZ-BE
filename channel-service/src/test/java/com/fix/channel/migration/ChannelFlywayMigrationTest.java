package com.fix.channel.migration;

import static org.assertj.core.api.Assertions.assertThat;

import db.migration.V8__backfill_order_session_uuid_contract;
import db.migration.V10__add_order_session_expires_at_contract;
import db.migration.V11__add_order_session_prepare_contract_columns;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.session.store-type=none"
})
class ChannelFlywayMigrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldCreateCoreTables() {
    Integer orderSessionTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'ORDER_SESSION'",
        Integer.class
    );
    Integer orderSessionsTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'ORDER_SESSIONS'",
        Integer.class
    );
    Integer orderSessionIdColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'ORDER_SESSION_ID'",
        Integer.class
    );
    Integer orderSessionExpiresAtColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'EXPIRES_AT'",
        Integer.class
    );
    Integer orderSessionAccountIdColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'ACCOUNT_ID'",
        Integer.class
    );
    Integer orderSessionSymbolColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'SYMBOL'",
        Integer.class
    );
    Integer membersTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'MEMBERS'",
        Integer.class
    );

    assertThat(orderSessionTableCount).isNotNull();
    assertThat(orderSessionTableCount).isEqualTo(1);
    assertThat(orderSessionsTableCount).isNotNull();
    assertThat(orderSessionsTableCount).isEqualTo(1);
    assertThat(orderSessionIdColumnCount).isNotNull();
    assertThat(orderSessionIdColumnCount).isEqualTo(1);
    assertThat(orderSessionExpiresAtColumnCount).isNotNull();
    assertThat(orderSessionExpiresAtColumnCount).isEqualTo(1);
    assertThat(orderSessionAccountIdColumnCount).isNotNull();
    assertThat(orderSessionAccountIdColumnCount).isEqualTo(1);
    assertThat(orderSessionSymbolColumnCount).isNotNull();
    assertThat(orderSessionSymbolColumnCount).isEqualTo(1);
    assertThat(membersTableCount).isNotNull();
    assertThat(membersTableCount).isEqualTo(1);
  }

  @Test
  void shouldBackfillLegacyOrderSessionUuidAndRemainIdempotent() throws Exception {
    String jdbcUrl = "jdbc:h2:mem:channel_migration_backfill;MODE=MySQL;DB_CLOSE_DELAY=-1";

    try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
         Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE order_sessions (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            member_id BIGINT NOT NULL,
            cl_ord_id VARCHAR(64) NOT NULL UNIQUE,
            order_ref VARCHAR(64) NOT NULL,
            status VARCHAR(32) NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            version BIGINT
          )
          """);
      statement.execute("""
          INSERT INTO order_sessions(member_id, cl_ord_id, order_ref, status, created_at, updated_at, version)
          VALUES (301, '123e4567-e89b-42d3-a456-426614174260', 'ORD-REF-LEGACY', 'PENDING_NEW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
          """);

      Context context = new TestFlywayContext(connection);
      V8__backfill_order_session_uuid_contract migration = new V8__backfill_order_session_uuid_contract();

      migration.migrate(context);
      migration.migrate(context);
    }

    JdbcTemplate backfillJdbcTemplate = new JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(jdbcUrl, "sa", "")
    );

    String orderSessionId = backfillJdbcTemplate.queryForObject(
        "SELECT order_session_id FROM order_sessions WHERE cl_ord_id = '123e4567-e89b-42d3-a456-426614174260'",
        String.class
    );
    Integer uniqueIndexCount = backfillJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND INDEX_NAME = 'UK_ORDER_SESSIONS_ORDER_SESSION_ID'",
        Integer.class
    );

    assertThat(orderSessionId).isNotBlank();
    assertThat(orderSessionId).hasSize(36);
    assertThat(uniqueIndexCount).isEqualTo(1);
  }

  @Test
  void shouldBackfillLegacyOrderSessionExpiryAndRemainIdempotent() throws Exception {
    String jdbcUrl = "jdbc:h2:mem:channel_migration_expiry;MODE=MySQL;DB_CLOSE_DELAY=-1";

    try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
         Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE order_sessions (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            order_session_id CHAR(36) NOT NULL,
            member_id BIGINT NOT NULL,
            cl_ord_id VARCHAR(64) NOT NULL UNIQUE,
            order_ref VARCHAR(64) NOT NULL,
            status VARCHAR(32) NOT NULL,
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            version BIGINT
          )
          """);
      statement.execute("""
          INSERT INTO order_sessions(order_session_id, member_id, cl_ord_id, order_ref, status, created_at, updated_at, version)
          VALUES (
            '123e4567-e89b-42d3-a456-426614174260',
            301,
            '123e4567-e89b-42d3-a456-426614174261',
            'ORD-REF-LEGACY',
            'PENDING_NEW',
            TIMESTAMP '2026-03-12 00:00:00',
            TIMESTAMP '2026-03-12 00:00:00',
            0
          )
          """);

      Context context = new TestFlywayContext(connection);
      V10__add_order_session_expires_at_contract migration = new V10__add_order_session_expires_at_contract();

      migration.migrate(context);
      migration.migrate(context);
    }

    JdbcTemplate expiryJdbcTemplate = new JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(jdbcUrl, "sa", "")
    );

    LocalDateTime expiresAt = expiryJdbcTemplate.queryForObject(
        "SELECT expires_at "
            + "FROM order_sessions WHERE cl_ord_id = '123e4567-e89b-42d3-a456-426614174261'",
        LocalDateTime.class
    );
    Integer expiryIndexCount = expiryJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND INDEX_NAME = 'IDX_ORDER_SESSIONS_STATUS_EXPIRES_AT'",
        Integer.class
    );

    assertThat(expiresAt).isNotNull();
    assertThat(expiresAt).isEqualTo(LocalDateTime.parse("2026-03-12T00:10:00"));
    assertThat(expiryIndexCount).isEqualTo(1);
  }

  @Test
  void shouldAddPrepareContractColumnsAndRemainIdempotent() throws Exception {
    String jdbcUrl = "jdbc:h2:mem:channel_migration_prepare_contract;MODE=MySQL;DB_CLOSE_DELAY=-1";

    try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
         Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE order_sessions (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            order_session_id CHAR(36) NOT NULL,
            member_id BIGINT NOT NULL,
            cl_ord_id VARCHAR(64) NOT NULL UNIQUE,
            order_ref VARCHAR(64) NOT NULL,
            status VARCHAR(32) NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            version BIGINT
          )
          """);

      Context context = new TestFlywayContext(connection);
      V11__add_order_session_prepare_contract_columns migration =
          new V11__add_order_session_prepare_contract_columns();

      migration.migrate(context);
      migration.migrate(context);
    }

    JdbcTemplate prepareContractJdbcTemplate = new JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(jdbcUrl, "sa", "")
    );

    Integer accountIdColumnCount = prepareContractJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'ACCOUNT_ID'",
        Integer.class
    );
    Integer symbolColumnCount = prepareContractJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'SYMBOL'",
        Integer.class
    );
    Integer qtyColumnCount = prepareContractJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'QTY'",
        Integer.class
    );

    assertThat(accountIdColumnCount).isEqualTo(1);
    assertThat(symbolColumnCount).isEqualTo(1);
    assertThat(qtyColumnCount).isEqualTo(1);
  }

  private record TestFlywayContext(Connection connection) implements Context {

    @Override
    public Configuration getConfiguration() {
      return null;
    }

    @Override
    public Connection getConnection() {
      return connection;
    }
  }
}
