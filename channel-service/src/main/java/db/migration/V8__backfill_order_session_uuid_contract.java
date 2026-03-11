package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V8__backfill_order_session_uuid_contract extends BaseJavaMigration {

  private static final String TABLE_NAME = "order_sessions";
  private static final String COLUMN_NAME = "order_session_id";
  private static final String INDEX_NAME = "uk_order_sessions_order_session_id";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    if (!columnExists(connection, TABLE_NAME, COLUMN_NAME)) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN order_session_id CHAR(36)");
      }
    }

    backfillMissingOrderSessionIds(connection);
    applyNotNullConstraint(connection);

    if (!indexExists(connection, TABLE_NAME, INDEX_NAME)) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE UNIQUE INDEX uk_order_sessions_order_session_id "
            + "ON order_sessions(order_session_id)");
      }
    }
  }

  private void backfillMissingOrderSessionIds(Connection connection) throws Exception {
    try (
        PreparedStatement select = connection.prepareStatement(
            "SELECT id FROM order_sessions WHERE order_session_id IS NULL"
        );
        ResultSet resultSet = select.executeQuery();
        PreparedStatement update = connection.prepareStatement(
            "UPDATE order_sessions SET order_session_id = ? WHERE id = ?"
        )
    ) {
      while (resultSet.next()) {
        update.setString(1, UUID.randomUUID().toString());
        update.setLong(2, resultSet.getLong("id"));
        update.addBatch();
      }
      update.executeBatch();
    }
  }

  private void applyNotNullConstraint(Connection connection) throws Exception {
    String databaseName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    try (Statement statement = connection.createStatement()) {
      if (databaseName.contains("h2")) {
        statement.execute("ALTER TABLE order_sessions ALTER COLUMN order_session_id SET NOT NULL");
      } else {
        statement.execute("ALTER TABLE order_sessions MODIFY COLUMN order_session_id CHAR(36) NOT NULL");
      }
    }
  }

  private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
      if (resultSet.next()) {
        return true;
      }
    }
    try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
      return resultSet.next();
    }
  }

  private boolean indexExists(Connection connection, String tableName, String indexName) throws Exception {
    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet resultSet = metaData.getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
      while (resultSet.next()) {
        String existingIndexName = resultSet.getString("INDEX_NAME");
        if (indexName.equalsIgnoreCase(existingIndexName)) {
          return true;
        }
      }
    }
    try (ResultSet resultSet = metaData.getIndexInfo(connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), false, false)) {
      while (resultSet.next()) {
        String existingIndexName = resultSet.getString("INDEX_NAME");
        if (indexName.equalsIgnoreCase(existingIndexName)) {
          return true;
        }
      }
    }
    return false;
  }
}
