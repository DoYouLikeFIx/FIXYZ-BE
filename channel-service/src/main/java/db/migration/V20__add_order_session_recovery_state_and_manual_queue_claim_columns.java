package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V20__add_order_session_recovery_state_and_manual_queue_claim_columns extends BaseJavaMigration {

  private static final String ORDER_SESSIONS_TABLE = "order_sessions";
  private static final String MANUAL_RECOVERY_QUEUE_TABLE = "manual_recovery_queue_entries";
  private static final String ORDER_SESSION_RECOVERY_CURSOR_INDEX =
      "idx_order_sessions_recovery_cursor";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      addColumnIfMissing(
          connection,
          statement,
          ORDER_SESSIONS_TABLE,
          "recovery_attempt_count",
          "ALTER TABLE " + ORDER_SESSIONS_TABLE + " ADD COLUMN recovery_attempt_count INT NULL"
      );
      addColumnIfMissing(
          connection,
          statement,
          ORDER_SESSIONS_TABLE,
          "recovery_next_attempt_at",
          "ALTER TABLE " + ORDER_SESSIONS_TABLE + " ADD COLUMN recovery_next_attempt_at TIMESTAMP NULL"
      );
      addColumnIfMissing(
          connection,
          statement,
          MANUAL_RECOVERY_QUEUE_TABLE,
          "publish_claim_token",
          "ALTER TABLE " + MANUAL_RECOVERY_QUEUE_TABLE + " ADD COLUMN publish_claim_token VARCHAR(64) NULL"
      );
      addColumnIfMissing(
          connection,
          statement,
          MANUAL_RECOVERY_QUEUE_TABLE,
          "publish_claimed_at",
          "ALTER TABLE " + MANUAL_RECOVERY_QUEUE_TABLE + " ADD COLUMN publish_claimed_at TIMESTAMP NULL"
      );
      createIndexIfMissing(
          connection,
          statement,
          ORDER_SESSIONS_TABLE,
          ORDER_SESSION_RECOVERY_CURSOR_INDEX,
          "CREATE INDEX " + ORDER_SESSION_RECOVERY_CURSOR_INDEX
              + " ON " + ORDER_SESSIONS_TABLE
              + "(status, recovery_next_attempt_at, updated_at, order_session_id)"
      );
    }
  }

  private void addColumnIfMissing(
      Connection connection,
      Statement statement,
      String tableName,
      String columnName,
      String ddl
  ) throws SQLException {
    if (columnExists(connection, tableName, columnName)) {
      return;
    }
    statement.execute(ddl);
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
