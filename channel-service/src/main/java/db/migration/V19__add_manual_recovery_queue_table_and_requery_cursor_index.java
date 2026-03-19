package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V19__add_manual_recovery_queue_table_and_requery_cursor_index extends BaseJavaMigration {

  private static final String ORDER_SESSIONS_TABLE = "order_sessions";
  private static final String MANUAL_RECOVERY_QUEUE_TABLE = "manual_recovery_queue_entries";
  private static final String ORDER_SESSION_REQUERY_CURSOR_INDEX =
      "idx_order_sessions_status_updated_at_session_id";
  private static final String MANUAL_RECOVERY_QUEUE_PENDING_INDEX =
      "idx_manual_recovery_queue_entries_published_at_enqueued_at";
  private static final String MANUAL_RECOVERY_QUEUE_ORDER_SESSION_INDEX =
      "uk_manual_recovery_queue_entries_order_session_id";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      createManualRecoveryQueueTableIfMissing(connection, statement);
      createIndexIfMissing(
          connection,
          statement,
          ORDER_SESSIONS_TABLE,
          ORDER_SESSION_REQUERY_CURSOR_INDEX,
          "CREATE INDEX " + ORDER_SESSION_REQUERY_CURSOR_INDEX
              + " ON " + ORDER_SESSIONS_TABLE + "(status, updated_at, order_session_id)"
      );
      createIndexIfMissing(
          connection,
          statement,
          MANUAL_RECOVERY_QUEUE_TABLE,
          MANUAL_RECOVERY_QUEUE_PENDING_INDEX,
          "CREATE INDEX " + MANUAL_RECOVERY_QUEUE_PENDING_INDEX
              + " ON " + MANUAL_RECOVERY_QUEUE_TABLE + "(published_at, enqueued_at)"
      );
      createIndexIfMissing(
          connection,
          statement,
          MANUAL_RECOVERY_QUEUE_TABLE,
          MANUAL_RECOVERY_QUEUE_ORDER_SESSION_INDEX,
          "CREATE UNIQUE INDEX " + MANUAL_RECOVERY_QUEUE_ORDER_SESSION_INDEX
              + " ON " + MANUAL_RECOVERY_QUEUE_TABLE + "(order_session_id)"
      );
    }
  }

  private void createManualRecoveryQueueTableIfMissing(Connection connection, Statement statement) throws SQLException {
    if (tableExists(connection, MANUAL_RECOVERY_QUEUE_TABLE)) {
      return;
    }
    statement.execute(
        "CREATE TABLE " + MANUAL_RECOVERY_QUEUE_TABLE + " ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
            + "order_session_id CHAR(36) NOT NULL, "
            + "cl_ord_id VARCHAR(64) NOT NULL, "
            + "attempt_count INT NOT NULL, "
            + "reason VARCHAR(64) NOT NULL, "
            + "enqueued_at TIMESTAMP NOT NULL, "
            + "published_at TIMESTAMP NULL, "
            + "created_at TIMESTAMP NOT NULL, "
            + "updated_at TIMESTAMP NOT NULL, "
            + "version BIGINT"
            + ")"
    );
  }

  private void createIndexIfMissing(
      Connection connection,
      Statement statement,
      String tableName,
      String indexName,
      String ddl
  ) throws SQLException {
    if (indexExists(connection, tableName, indexName)) {
      return;
    }
    statement.execute(ddl);
  }

  private boolean tableExists(Connection connection, String tableName) throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, tableName, null)) {
      if (tables.next()) {
        return true;
      }
    }
    try (ResultSet tables = metaData.getTables(
        connection.getCatalog(),
        null,
        tableName.toUpperCase(Locale.ROOT),
        null
    )) {
      return tables.next();
    }
  }

  private boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet indexes = metaData.getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
      while (indexes.next()) {
        String existingIndexName = indexes.getString("INDEX_NAME");
        if (indexName.equalsIgnoreCase(existingIndexName)) {
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
        String existingIndexName = indexes.getString("INDEX_NAME");
        if (indexName.equalsIgnoreCase(existingIndexName)) {
          return true;
        }
      }
    }
    return false;
  }
}
