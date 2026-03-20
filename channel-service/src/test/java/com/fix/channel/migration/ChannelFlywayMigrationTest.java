package com.fix.channel.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.web.CorrelationIdSupport;
import db.migration.V8__backfill_order_session_uuid_contract;
import db.migration.V10__add_order_session_expires_at_contract;
import db.migration.V11__add_order_session_prepare_contract_columns;
import db.migration.V13__add_order_session_authorization_decision_columns;
import db.migration.V14__add_order_session_execution_columns;
import db.migration.V15__add_order_session_external_sync_status_column;
import db.migration.V17__align_audit_security_event_contract;
import db.migration.V22__add_order_session_quote_context_columns;
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
    Integer orderSessionChallengeRequiredColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'CHALLENGE_REQUIRED'",
        Integer.class
    );
    Integer orderSessionAuthorizationReasonColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'AUTHORIZATION_REASON'",
        Integer.class
    );
    Integer orderSessionExecutionResultColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'EXECUTION_RESULT'",
        Integer.class
    );
    Integer orderSessionExecutedAtColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'EXECUTED_AT'",
        Integer.class
    );
    Integer orderSessionExternalSyncStatusColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'EXTERNAL_SYNC_STATUS'",
        Integer.class
    );
    Integer orderSessionRecoveryAttemptCountColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'RECOVERY_ATTEMPT_COUNT'",
        Integer.class
    );
    Integer orderSessionRecoveryNextAttemptAtColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'RECOVERY_NEXT_ATTEMPT_AT'",
        Integer.class
    );
    Integer orderSessionQuoteSnapshotIdColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'QUOTE_SNAPSHOT_ID'",
        Integer.class
    );
    Integer orderSessionQuoteAsOfColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'QUOTE_AS_OF'",
        Integer.class
    );
    Integer orderSessionQuoteSourceModeColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'QUOTE_SOURCE_MODE'",
        Integer.class
    );
    Integer orderSessionPreTradePriceColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'PRE_TRADE_PRICE'",
        Integer.class
    );
    Integer orderSessionRecoveryCursorIndexCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' "
            + "AND INDEX_NAME = 'IDX_ORDER_SESSIONS_RECOVERY_CURSOR'",
        Integer.class
    );
    Integer manualRecoveryQueueTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'MANUAL_RECOVERY_QUEUE_ENTRIES'",
        Integer.class
    );
    Integer manualRecoveryPublishClaimTokenColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'MANUAL_RECOVERY_QUEUE_ENTRIES' AND COLUMN_NAME = 'PUBLISH_CLAIM_TOKEN'",
        Integer.class
    );
    Integer manualRecoveryPublishClaimedAtColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'MANUAL_RECOVERY_QUEUE_ENTRIES' AND COLUMN_NAME = 'PUBLISH_CLAIMED_AT'",
        Integer.class
    );
    Integer membersTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'MEMBERS'",
        Integer.class
    );
    Integer auditUuidColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'AUDIT_LOGS' AND COLUMN_NAME = 'AUDIT_UUID'",
        Integer.class
    );
    Integer auditOrderSessionIdColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'AUDIT_LOGS' AND COLUMN_NAME = 'ORDER_SESSION_ID'",
        Integer.class
    );
    Integer auditCorrelationUuidColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'AUDIT_LOGS' AND COLUMN_NAME = 'CORRELATION_UUID'",
        Integer.class
    );
    Integer auditTargetIdLength = jdbcTemplate.queryForObject(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'AUDIT_LOGS' AND COLUMN_NAME = 'TARGET_ID'",
        Integer.class
    );
    Integer auditIpAddressLength = jdbcTemplate.queryForObject(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'AUDIT_LOGS' AND COLUMN_NAME = 'IP_ADDRESS'",
        Integer.class
    );
    Integer auditUserAgentLength = jdbcTemplate.queryForObject(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'AUDIT_LOGS' AND COLUMN_NAME = 'USER_AGENT'",
        Integer.class
    );
    Integer securityEventUuidColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'SECURITY_EVENTS' AND COLUMN_NAME = 'SECURITY_EVENT_UUID'",
        Integer.class
    );
    Integer securityStatusColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'SECURITY_EVENTS' AND COLUMN_NAME = 'STATUS'",
        Integer.class
    );
    Integer securityIpAddressLength = jdbcTemplate.queryForObject(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'SECURITY_EVENTS' AND COLUMN_NAME = 'IP_ADDRESS'",
        Integer.class
    );
    Integer securityAdminMemberIdColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'SECURITY_EVENTS' AND COLUMN_NAME = 'ADMIN_MEMBER_ID'",
        Integer.class
    );
    Integer securityOccurredAtColumnCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'SECURITY_EVENTS' AND COLUMN_NAME = 'OCCURRED_AT'",
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
    assertThat(orderSessionChallengeRequiredColumnCount).isNotNull();
    assertThat(orderSessionChallengeRequiredColumnCount).isEqualTo(1);
    assertThat(orderSessionAuthorizationReasonColumnCount).isNotNull();
    assertThat(orderSessionAuthorizationReasonColumnCount).isEqualTo(1);
    assertThat(orderSessionExecutionResultColumnCount).isNotNull();
    assertThat(orderSessionExecutionResultColumnCount).isEqualTo(1);
    assertThat(orderSessionExecutedAtColumnCount).isNotNull();
    assertThat(orderSessionExecutedAtColumnCount).isEqualTo(1);
    assertThat(orderSessionExternalSyncStatusColumnCount).isNotNull();
    assertThat(orderSessionExternalSyncStatusColumnCount).isEqualTo(1);
    assertThat(orderSessionRecoveryAttemptCountColumnCount).isEqualTo(1);
    assertThat(orderSessionRecoveryNextAttemptAtColumnCount).isEqualTo(1);
    assertThat(orderSessionQuoteSnapshotIdColumnCount).isEqualTo(1);
    assertThat(orderSessionQuoteAsOfColumnCount).isEqualTo(1);
    assertThat(orderSessionQuoteSourceModeColumnCount).isEqualTo(1);
    assertThat(orderSessionPreTradePriceColumnCount).isEqualTo(1);
    assertThat(orderSessionRecoveryCursorIndexCount).isEqualTo(1);
    assertThat(manualRecoveryQueueTableCount).isEqualTo(1);
    assertThat(manualRecoveryPublishClaimTokenColumnCount).isEqualTo(1);
    assertThat(manualRecoveryPublishClaimedAtColumnCount).isEqualTo(1);
    assertThat(membersTableCount).isNotNull();
    assertThat(membersTableCount).isEqualTo(1);
    assertThat(auditUuidColumnCount).isEqualTo(1);
    assertThat(auditOrderSessionIdColumnCount).isEqualTo(1);
    assertThat(auditCorrelationUuidColumnCount).isEqualTo(1);
    assertThat(auditTargetIdLength).isEqualTo(100);
    assertThat(auditIpAddressLength).isEqualTo(45);
    assertThat(auditUserAgentLength).isEqualTo(1000);
    assertThat(securityEventUuidColumnCount).isEqualTo(1);
    assertThat(securityStatusColumnCount).isEqualTo(1);
    assertThat(securityIpAddressLength).isEqualTo(45);
    assertThat(securityAdminMemberIdColumnCount).isEqualTo(1);
    assertThat(securityOccurredAtColumnCount).isEqualTo(1);
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

  @Test
  void shouldAddAuthorizationDecisionColumnsAndBackfillLegacySessions() throws Exception {
    String jdbcUrl = "jdbc:h2:mem:channel_migration_authorization_contract;MODE=MySQL;DB_CLOSE_DELAY=-1";

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
            account_id BIGINT,
            symbol VARCHAR(16),
            side VARCHAR(16),
            order_type VARCHAR(16),
            qty DECIMAL(19, 4),
            price DECIMAL(19, 4),
            expires_at TIMESTAMP NOT NULL,
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            version BIGINT
          )
          """);
      statement.execute("""
          INSERT INTO order_sessions(
            order_session_id,
            member_id,
            cl_ord_id,
            order_ref,
            status,
            expires_at,
            created_at,
            updated_at,
            version
          )
          VALUES (
            '123e4567-e89b-42d3-a456-426614174262',
            301,
            '123e4567-e89b-42d3-a456-426614174263',
            'ORD-REF-LEGACY',
            'PENDING_NEW',
            TIMESTAMP '2026-03-12 00:10:00',
            TIMESTAMP '2026-03-12 00:00:00',
            TIMESTAMP '2026-03-12 00:00:00',
            0
          )
          """);

      Context context = new TestFlywayContext(connection);
      V13__add_order_session_authorization_decision_columns migration =
          new V13__add_order_session_authorization_decision_columns();

      migration.migrate(context);
      migration.migrate(context);
    }

    JdbcTemplate authorizationJdbcTemplate = new JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(jdbcUrl, "sa", "")
    );

    Boolean challengeRequired = authorizationJdbcTemplate.queryForObject(
        "SELECT challenge_required FROM order_sessions "
            + "WHERE cl_ord_id = '123e4567-e89b-42d3-a456-426614174263'",
        Boolean.class
    );
    String authorizationReason = authorizationJdbcTemplate.queryForObject(
        "SELECT authorization_reason FROM order_sessions "
            + "WHERE cl_ord_id = '123e4567-e89b-42d3-a456-426614174263'",
        String.class
    );

    assertThat(challengeRequired).isTrue();
    assertThat(authorizationReason).isEqualTo("ELEVATED_ORDER_RISK");
  }

  @Test
  void shouldAddExecutionColumnsAndRemainIdempotent() throws Exception {
    String jdbcUrl = "jdbc:h2:mem:channel_migration_execution_contract;MODE=MySQL;DB_CLOSE_DELAY=-1";

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
            account_id BIGINT,
            symbol VARCHAR(16),
            side VARCHAR(16),
            order_type VARCHAR(16),
            qty DECIMAL(19, 4),
            price DECIMAL(19, 4),
            challenge_required BOOLEAN NOT NULL,
            authorization_reason VARCHAR(64) NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            version BIGINT
          )
          """);

      Context context = new TestFlywayContext(connection);
      V14__add_order_session_execution_columns migration = new V14__add_order_session_execution_columns();

      migration.migrate(context);
      migration.migrate(context);
    }

    JdbcTemplate executionJdbcTemplate = new JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(jdbcUrl, "sa", "")
    );

    Integer executionResultColumnCount = executionJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'EXECUTION_RESULT'",
        Integer.class
    );
    Integer executedQtyColumnCount = executionJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'EXECUTED_QTY'",
        Integer.class
    );
    Integer failureReasonColumnCount = executionJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'FAILURE_REASON'",
        Integer.class
    );

    assertThat(executionResultColumnCount).isEqualTo(1);
    assertThat(executedQtyColumnCount).isEqualTo(1);
    assertThat(failureReasonColumnCount).isEqualTo(1);
  }

  @Test
  void shouldAddExternalSyncStatusColumnAndRemainIdempotent() throws Exception {
    String jdbcUrl = "jdbc:h2:mem:channel_migration_external_sync_status;MODE=MySQL;DB_CLOSE_DELAY=-1";

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
            account_id BIGINT,
            symbol VARCHAR(16),
            side VARCHAR(16),
            order_type VARCHAR(16),
            qty DECIMAL(19, 4),
            price DECIMAL(19, 4),
            challenge_required BOOLEAN NOT NULL,
            authorization_reason VARCHAR(64) NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            execution_result VARCHAR(32),
            executed_qty DECIMAL(19, 4),
            leaves_qty DECIMAL(19, 4),
            executed_price DECIMAL(19, 4),
            external_order_id VARCHAR(64),
            failure_reason VARCHAR(64),
            executed_at TIMESTAMP,
            canceled_at TIMESTAMP,
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            version BIGINT
          )
          """);

      Context context = new TestFlywayContext(connection);
      V15__add_order_session_external_sync_status_column migration =
          new V15__add_order_session_external_sync_status_column();

      migration.migrate(context);
      migration.migrate(context);
    }

    JdbcTemplate externalSyncStatusJdbcTemplate = new JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(jdbcUrl, "sa", "")
    );

    Integer externalSyncStatusColumnCount = externalSyncStatusJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'EXTERNAL_SYNC_STATUS'",
        Integer.class
    );

    assertThat(externalSyncStatusColumnCount).isEqualTo(1);
  }

  @Test
  void shouldAlignAuditAndSecurityContractsAndRemainIdempotent() throws Exception {
    String jdbcUrl = "jdbc:h2:mem:channel_migration_audit_security_contract;MODE=MySQL;DB_CLOSE_DELAY=-1";
    String legacyCorrelationId = "trace-channel-auth-very-long-correlation-id-000001";

    try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
         Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE order_sessions (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            order_session_id CHAR(36) NOT NULL
          )
          """);
      statement.execute("""
          CREATE TABLE audit_logs (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            member_id BIGINT,
            action VARCHAR(64) NOT NULL,
            target_type VARCHAR(64) NOT NULL,
            target_id VARCHAR(64),
            detail VARCHAR(1000),
            ip_address VARCHAR(64),
            user_agent VARCHAR(255),
            correlation_id VARCHAR(128),
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            version BIGINT
          )
          """);
      statement.execute("""
          CREATE TABLE security_events (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            member_id BIGINT,
            event_type VARCHAR(64) NOT NULL,
            ip_address VARCHAR(64),
            user_agent VARCHAR(255),
            severity VARCHAR(32) NOT NULL,
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            version BIGINT
          )
          """);
      statement.execute("""
          INSERT INTO order_sessions(id, order_session_id)
          VALUES (777, '123e4567-e89b-42d3-a456-426614174260')
          """);
      statement.execute("""
          INSERT INTO audit_logs(
            member_id, action, target_type, target_id, detail, ip_address, user_agent, correlation_id, created_at, updated_at, version
          )
          VALUES (
            101,
            'ORDER_SESSION_CREATE',
            'ORDER_SESSION',
            '123e4567-e89b-42d3-a456-426614174260',
            'legacy audit row',
            '127.0.0.1',
            'JUnit',
            '%s',
            TIMESTAMP '2026-03-12 00:00:00',
            TIMESTAMP '2026-03-12 00:00:00',
            0
          )
          """.formatted(legacyCorrelationId));
      statement.execute("""
          INSERT INTO security_events(
            member_id, event_type, ip_address, user_agent, severity, created_at, updated_at, version
          )
          VALUES (
            101,
            'ACCOUNT_LOCKED',
            '127.0.0.1',
            'JUnit',
            'HIGH',
            TIMESTAMP '2026-03-12 00:00:00',
            TIMESTAMP '2026-03-12 00:00:00',
            0
          )
          """);

      Context context = new TestFlywayContext(connection);
      V17__align_audit_security_event_contract migration = new V17__align_audit_security_event_contract();

      migration.migrate(context);
      migration.migrate(context);
    }

    JdbcTemplate contractJdbcTemplate = new JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(jdbcUrl, "sa", "")
    );

    String auditUuid = contractJdbcTemplate.queryForObject(
        "SELECT audit_uuid FROM audit_logs WHERE action = 'ORDER_SESSION_CREATE'",
        String.class
    );
    Long auditOrderSessionId = contractJdbcTemplate.queryForObject(
        "SELECT order_session_id FROM audit_logs WHERE action = 'ORDER_SESSION_CREATE'",
        Long.class
    );
    String correlationUuid = contractJdbcTemplate.queryForObject(
        "SELECT correlation_uuid FROM audit_logs WHERE action = 'ORDER_SESSION_CREATE'",
        String.class
    );
    String securityEventUuid = contractJdbcTemplate.queryForObject(
        "SELECT security_event_uuid FROM security_events WHERE event_type = 'ACCOUNT_LOCKED'",
        String.class
    );
    String securityStatus = contractJdbcTemplate.queryForObject(
        "SELECT status FROM security_events WHERE event_type = 'ACCOUNT_LOCKED'",
        String.class
    );
    LocalDateTime occurredAt = contractJdbcTemplate.queryForObject(
        "SELECT occurred_at FROM security_events WHERE event_type = 'ACCOUNT_LOCKED'",
        LocalDateTime.class
    );
    Integer auditUuidIndexCount = contractJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'AUDIT_LOGS' AND INDEX_NAME = 'UK_AUDIT_LOGS_AUDIT_UUID'",
        Integer.class
    );
    Integer securityUuidIndexCount = contractJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'SECURITY_EVENTS' AND INDEX_NAME = 'UK_SECURITY_EVENTS_SECURITY_EVENT_UUID'",
        Integer.class
    );
    String auditUuidNullable = contractJdbcTemplate.queryForObject(
        "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'AUDIT_LOGS' AND COLUMN_NAME = 'AUDIT_UUID'",
        String.class
    );
    String securityEventUuidNullable = contractJdbcTemplate.queryForObject(
        "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'SECURITY_EVENTS' AND COLUMN_NAME = 'SECURITY_EVENT_UUID'",
        String.class
    );
    String securityStatusNullable = contractJdbcTemplate.queryForObject(
        "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'SECURITY_EVENTS' AND COLUMN_NAME = 'STATUS'",
        String.class
    );
    String securityOccurredAtNullable = contractJdbcTemplate.queryForObject(
        "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'SECURITY_EVENTS' AND COLUMN_NAME = 'OCCURRED_AT'",
        String.class
    );

    assertThat(auditUuid).isNotBlank();
    assertThat(auditOrderSessionId).isEqualTo(777L);
    assertThat(correlationUuid).isEqualTo(CorrelationIdSupport.normalize(legacyCorrelationId, 36));
    assertThat(securityEventUuid).isNotBlank();
    assertThat(securityStatus).isEqualTo("OPEN");
    assertThat(occurredAt).isEqualTo(LocalDateTime.parse("2026-03-12T00:00:00"));
    assertThat(auditUuidIndexCount).isEqualTo(1);
    assertThat(securityUuidIndexCount).isEqualTo(1);
    assertThat(auditUuidNullable).isEqualTo("NO");
    assertThat(securityEventUuidNullable).isEqualTo("NO");
    assertThat(securityStatusNullable).isEqualTo("NO");
    assertThat(securityOccurredAtNullable).isEqualTo("NO");
  }

  @Test
  void shouldAddQuoteContextColumnsAndRemainIdempotent() throws Exception {
    String jdbcUrl = "jdbc:h2:mem:channel_migration_quote_context;MODE=MySQL;DB_CLOSE_DELAY=-1";

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
            account_id BIGINT,
            symbol VARCHAR(16),
            side VARCHAR(16),
            order_type VARCHAR(16),
            qty DECIMAL(19, 4),
            price DECIMAL(19, 4),
            challenge_required BOOLEAN NOT NULL,
            authorization_reason VARCHAR(64) NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            version BIGINT
          )
          """);

      Context context = new TestFlywayContext(connection);
      V22__add_order_session_quote_context_columns migration =
          new V22__add_order_session_quote_context_columns();

      migration.migrate(context);
      migration.migrate(context);
    }

    JdbcTemplate quoteContextJdbcTemplate = new JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(jdbcUrl, "sa", "")
    );

    Integer quoteSnapshotIdColumnCount = quoteContextJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'QUOTE_SNAPSHOT_ID'",
        Integer.class
    );
    Integer quoteAsOfColumnCount = quoteContextJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'QUOTE_AS_OF'",
        Integer.class
    );
    Integer quoteSourceModeColumnCount = quoteContextJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'QUOTE_SOURCE_MODE'",
        Integer.class
    );
    Integer preTradePriceColumnCount = quoteContextJdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDER_SESSIONS' AND COLUMN_NAME = 'PRE_TRADE_PRICE'",
        Integer.class
    );

    assertThat(quoteSnapshotIdColumnCount).isEqualTo(1);
    assertThat(quoteAsOfColumnCount).isEqualTo(1);
    assertThat(quoteSourceModeColumnCount).isEqualTo(1);
    assertThat(preTradePriceColumnCount).isEqualTo(1);
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
