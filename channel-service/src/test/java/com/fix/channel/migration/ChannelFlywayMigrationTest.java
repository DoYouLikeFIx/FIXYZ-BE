package com.fix.channel.migration;

import static org.assertj.core.api.Assertions.assertThat;

import db.migration.V8__backfill_order_session_uuid_contract;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
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
  void shouldBackfillAuthorizedMetadataForLegacyAuthorizedAndOnlyPostAuthExpiredSessions() throws Exception {
    String jdbcUrl = "jdbc:h2:mem:channel_migration_auth_metadata;MODE=MySQL;DB_CLOSE_DELAY=-1";

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
          VALUES
            (301, '123e4567-e89b-42d3-a456-426614174360', 'ORD-REF-AUTHED', 'AUTHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
            (302, '123e4567-e89b-42d3-a456-426614174361', 'ORD-REF-EXECUTING', 'EXECUTING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 2),
            (303, '123e4567-e89b-42d3-a456-426614174362', 'ORD-REF-REQUERYING', 'REQUERYING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 3),
            (304, '123e4567-e89b-42d3-a456-426614174363', 'ORD-REF-ESCALATED', 'ESCALATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 4),
            (305, '123e4567-e89b-42d3-a456-426614174364', 'ORD-REF-COMPLETED', 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 5),
            (306, '123e4567-e89b-42d3-a456-426614174365', 'ORD-REF-FAILED', 'FAILED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 6),
            (307, '123e4567-e89b-42d3-a456-426614174366', 'ORD-REF-CANCELED', 'CANCELED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 7),
            (308, '123e4567-e89b-42d3-a456-426614174367', 'ORD-REF-AUTHED-EXPIRED', 'EXPIRED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 2),
            (309, '123e4567-e89b-42d3-a456-426614174368', 'ORD-REF-PENDING-EXPIRED', 'EXPIRED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
            (310, '123e4567-e89b-42d3-a456-426614174369', 'ORD-REF-PENDING', 'PENDING_NEW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
          """);
    }

    DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, "sa", "");
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
        new ClassPathResource("db/migration/V9__add_order_session_authorization_metadata.sql")
    );
    populator.execute(dataSource);

    JdbcTemplate authMetadataJdbcTemplate = new JdbcTemplate(dataSource);

    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174360",
        false,
        "OTP_VERIFIED"
    );
    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174361",
        false,
        "OTP_VERIFIED"
    );
    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174362",
        false,
        "OTP_VERIFIED"
    );
    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174363",
        false,
        "OTP_VERIFIED"
    );
    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174364",
        false,
        "OTP_VERIFIED"
    );
    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174365",
        false,
        "OTP_VERIFIED"
    );
    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174366",
        false,
        "OTP_VERIFIED"
    );
    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174367",
        false,
        "OTP_VERIFIED"
    );
    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174368",
        true,
        "STEP_UP_REQUIRED"
    );
    assertAuthorizationMetadata(
        authMetadataJdbcTemplate,
        "123e4567-e89b-42d3-a456-426614174369",
        true,
        "STEP_UP_REQUIRED"
    );
  }

  private void assertAuthorizationMetadata(
      JdbcTemplate jdbcTemplate,
      String clOrdId,
      boolean challengeRequired,
      String authorizationReason
  ) {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT challenge_required FROM order_sessions WHERE cl_ord_id = ?",
        Boolean.class,
        clOrdId
    )).isEqualTo(challengeRequired);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT authorization_reason FROM order_sessions WHERE cl_ord_id = ?",
        String.class,
        clOrdId
    )).isEqualTo(authorizationReason);
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
