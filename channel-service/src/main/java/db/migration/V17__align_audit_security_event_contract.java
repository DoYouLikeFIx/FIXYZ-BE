package db.migration;

import com.fix.common.web.CorrelationIdSupport;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V17__align_audit_security_event_contract extends BaseJavaMigration {

  private static final String AUDIT_TABLE = "audit_logs";
  private static final String SECURITY_TABLE = "security_events";
  private static final String ORDER_SESSION_TABLE = "order_sessions";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      alignAuditLogs(connection, statement);
      alignSecurityEvents(connection, statement);
    }
  }

  private void alignAuditLogs(Connection connection, Statement statement) throws SQLException {
    addColumnIfMissing(connection, statement, AUDIT_TABLE, "audit_uuid", "CHAR(36)");
    addColumnIfMissing(connection, statement, AUDIT_TABLE, "order_session_id", "BIGINT");
    addColumnIfMissing(connection, statement, AUDIT_TABLE, "correlation_uuid", "CHAR(36)");

    backfillAuditUuids(connection);
    backfillAuditOrderSessionIds(connection);
    backfillAuditCorrelationUuids(connection);
    applyAuditColumnContracts(connection, statement);
    applyAuditNotNullConstraints(connection, statement);

    createIndexIfMissing(connection, statement, AUDIT_TABLE, "UK_AUDIT_LOGS_AUDIT_UUID", true, "audit_uuid");
    createIndexIfMissing(
        connection,
        statement,
        AUDIT_TABLE,
        "IDX_AUDIT_LOGS_MEMBER_ACTION_CREATED_AT",
        false,
        "member_id, action, created_at"
    );
    createIndexIfMissing(
        connection,
        statement,
        AUDIT_TABLE,
        "IDX_AUDIT_LOGS_ORDER_SESSION_ID_CREATED_AT",
        false,
        "order_session_id, created_at"
    );
    createIndexIfMissing(connection, statement, AUDIT_TABLE, "IDX_AUDIT_LOGS_ACTION_CREATED_AT", false, "action, created_at");
  }

  private void alignSecurityEvents(Connection connection, Statement statement) throws SQLException {
    addColumnIfMissing(connection, statement, SECURITY_TABLE, "security_event_uuid", "CHAR(36)");
    addColumnIfMissing(connection, statement, SECURITY_TABLE, "status", "VARCHAR(32)");
    addColumnIfMissing(connection, statement, SECURITY_TABLE, "admin_member_id", "BIGINT");
    addColumnIfMissing(connection, statement, SECURITY_TABLE, "order_session_id", "BIGINT");
    addColumnIfMissing(connection, statement, SECURITY_TABLE, "detail", "VARCHAR(2000)");
    addColumnIfMissing(connection, statement, SECURITY_TABLE, "correlation_uuid", "CHAR(36)");
    addColumnIfMissing(connection, statement, SECURITY_TABLE, "occurred_at", "TIMESTAMP");
    addColumnIfMissing(connection, statement, SECURITY_TABLE, "resolved_at", "TIMESTAMP");

    backfillSecurityEventUuids(connection);
    backfillSecurityEventStatus(connection);
    backfillSecurityEventOccurredAt(connection);
    applySecurityEventColumnContracts(connection, statement);
    applySecurityEventNotNullConstraints(connection, statement);

    createIndexIfMissing(
        connection,
        statement,
        SECURITY_TABLE,
        "UK_SECURITY_EVENTS_SECURITY_EVENT_UUID",
        true,
        "security_event_uuid"
    );
    createIndexIfMissing(
        connection,
        statement,
        SECURITY_TABLE,
        "IDX_SECURITY_EVENTS_STATUS_SEVERITY_OCCURRED_AT",
        false,
        "status, severity, occurred_at"
    );
    createIndexIfMissing(
        connection,
        statement,
        SECURITY_TABLE,
        "IDX_SECURITY_EVENTS_MEMBER_OCCURRED_AT",
        false,
        "member_id, occurred_at"
    );
    createIndexIfMissing(
        connection,
        statement,
        SECURITY_TABLE,
        "IDX_SECURITY_EVENTS_TYPE_OCCURRED_AT",
        false,
        "event_type, occurred_at"
    );
    createIndexIfMissing(connection, statement, SECURITY_TABLE, "IDX_SECURITY_EVENTS_ADMIN_MEMBER_ID", false, "admin_member_id");
  }

  private void backfillAuditUuids(Connection connection) throws SQLException {
    List<Long> ids = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT id FROM audit_logs WHERE audit_uuid IS NULL OR audit_uuid = ''"
    ); ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        ids.add(resultSet.getLong(1));
      }
    }
    try (PreparedStatement update = connection.prepareStatement(
        "UPDATE audit_logs SET audit_uuid = ? WHERE id = ?"
    )) {
      for (Long id : ids) {
        update.setString(1, UUID.randomUUID().toString());
        update.setLong(2, id);
        update.addBatch();
      }
      update.executeBatch();
    }
  }

  private void backfillAuditOrderSessionIds(Connection connection) throws SQLException {
    if (!columnExists(connection, ORDER_SESSION_TABLE, "order_session_id")) {
      return;
    }
    List<AuditOrderSessionBinding> bindings = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT l.id, s.id
        FROM audit_logs l
        JOIN order_sessions s
          ON l.target_type = 'ORDER_SESSION'
         AND l.target_id = s.order_session_id
        WHERE l.order_session_id IS NULL
        """);
         ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        bindings.add(new AuditOrderSessionBinding(resultSet.getLong(1), resultSet.getLong(2)));
      }
    }
    try (PreparedStatement update = connection.prepareStatement(
        "UPDATE audit_logs SET order_session_id = ? WHERE id = ?"
    )) {
      for (AuditOrderSessionBinding binding : bindings) {
        update.setLong(1, binding.orderSessionId());
        update.setLong(2, binding.auditLogId());
        update.addBatch();
      }
      update.executeBatch();
    }
  }

  private void backfillAuditCorrelationUuids(Connection connection) throws SQLException {
    if (!columnExists(connection, AUDIT_TABLE, "correlation_id")) {
      return;
    }
    List<AuditCorrelationBinding> bindings = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT id, correlation_id
          FROM audit_logs
         WHERE (correlation_uuid IS NULL OR correlation_uuid = '')
           AND correlation_id IS NOT NULL
           AND correlation_id <> ''
        """);
         ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        bindings.add(new AuditCorrelationBinding(
            resultSet.getLong(1),
            CorrelationIdSupport.normalize(resultSet.getString(2), 36)
        ));
      }
    }
    try (PreparedStatement update = connection.prepareStatement(
        "UPDATE audit_logs SET correlation_uuid = ? WHERE id = ?"
    )) {
      for (AuditCorrelationBinding binding : bindings) {
        update.setString(1, binding.correlationUuid());
        update.setLong(2, binding.auditLogId());
        update.addBatch();
      }
      update.executeBatch();
    }
  }

  private void backfillSecurityEventUuids(Connection connection) throws SQLException {
    List<Long> ids = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT id FROM security_events WHERE security_event_uuid IS NULL OR security_event_uuid = ''"
    ); ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        ids.add(resultSet.getLong(1));
      }
    }
    try (PreparedStatement update = connection.prepareStatement(
        "UPDATE security_events SET security_event_uuid = ? WHERE id = ?"
    )) {
      for (Long id : ids) {
        update.setString(1, UUID.randomUUID().toString());
        update.setLong(2, id);
        update.addBatch();
      }
      update.executeBatch();
    }
  }

  private void backfillSecurityEventStatus(Connection connection) throws SQLException {
    try (PreparedStatement update = connection.prepareStatement("""
        UPDATE security_events
           SET status = 'OPEN'
         WHERE status IS NULL OR status = ''
        """)) {
      update.executeUpdate();
    }
  }

  private void backfillSecurityEventOccurredAt(Connection connection) throws SQLException {
    try (PreparedStatement update = connection.prepareStatement("""
        UPDATE security_events
           SET occurred_at = created_at
         WHERE occurred_at IS NULL
        """)) {
      update.executeUpdate();
    }
  }

  private void addColumnIfMissing(
      Connection connection,
      Statement statement,
      String tableName,
      String columnName,
      String columnDefinition
  ) throws SQLException {
    if (columnExists(connection, tableName, columnName)) {
      return;
    }
    statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
  }

  private void createIndexIfMissing(
      Connection connection,
      Statement statement,
      String tableName,
      String indexName,
      boolean unique,
      String columns
  ) throws SQLException {
    if (indexExists(connection, tableName, indexName)) {
      return;
    }
    String prefix = unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ";
    statement.execute(prefix + indexName + " ON " + tableName + "(" + columns + ")");
  }

  private void applyAuditColumnContracts(Connection connection, Statement statement) throws SQLException {
    String databaseName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    if (databaseName.contains("h2")) {
      statement.execute("ALTER TABLE audit_logs ALTER COLUMN target_id VARCHAR(100)");
      statement.execute("ALTER TABLE audit_logs ALTER COLUMN user_agent VARCHAR(1000)");
      statement.execute("ALTER TABLE audit_logs ALTER COLUMN ip_address VARCHAR(45)");
      return;
    }
    statement.execute("ALTER TABLE audit_logs MODIFY COLUMN target_id VARCHAR(100)");
    statement.execute("ALTER TABLE audit_logs MODIFY COLUMN user_agent VARCHAR(1000)");
    statement.execute("ALTER TABLE audit_logs MODIFY COLUMN ip_address VARCHAR(45)");
  }

  private void applySecurityEventColumnContracts(Connection connection, Statement statement) throws SQLException {
    String databaseName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    if (databaseName.contains("h2")) {
      statement.execute("ALTER TABLE security_events ALTER COLUMN ip_address VARCHAR(45)");
      return;
    }
    statement.execute("ALTER TABLE security_events MODIFY COLUMN ip_address VARCHAR(45)");
  }

  private void applyAuditNotNullConstraints(Connection connection, Statement statement) throws SQLException {
    String databaseName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    if (databaseName.contains("h2")) {
      statement.execute("ALTER TABLE audit_logs ALTER COLUMN audit_uuid SET NOT NULL");
      return;
    }
    statement.execute("ALTER TABLE audit_logs MODIFY COLUMN audit_uuid CHAR(36) NOT NULL");
  }

  private void applySecurityEventNotNullConstraints(Connection connection, Statement statement) throws SQLException {
    String databaseName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    if (databaseName.contains("h2")) {
      statement.execute("ALTER TABLE security_events ALTER COLUMN security_event_uuid SET NOT NULL");
      statement.execute("ALTER TABLE security_events ALTER COLUMN status SET NOT NULL");
      statement.execute("ALTER TABLE security_events ALTER COLUMN occurred_at SET NOT NULL");
      return;
    }
    statement.execute("ALTER TABLE security_events MODIFY COLUMN security_event_uuid CHAR(36) NOT NULL");
    statement.execute("ALTER TABLE security_events MODIFY COLUMN status VARCHAR(32) NOT NULL");
    statement.execute("ALTER TABLE security_events MODIFY COLUMN occurred_at TIMESTAMP NOT NULL");
  }

  private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
      if (columns.next()) {
        return true;
      }
    }
    try (ResultSet columns = metaData.getColumns(
        connection.getCatalog(),
        null,
        tableName.toUpperCase(Locale.ROOT),
        columnName.toUpperCase(Locale.ROOT)
    )) {
      return columns.next();
    }
  }

  private boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet indexes = metaData.getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
      while (indexes.next()) {
        if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
          return true;
        }
      }
    }
    try (ResultSet indexes = metaData.getIndexInfo(
        connection.getCatalog(),
        null,
        tableName.toUpperCase(Locale.ROOT),
        false,
        false
    )) {
      while (indexes.next()) {
        if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
          return true;
        }
      }
    }
    return false;
  }

  private record AuditOrderSessionBinding(long auditLogId, long orderSessionId) {
  }

  private record AuditCorrelationBinding(long auditLogId, String correlationUuid) {
  }
}
