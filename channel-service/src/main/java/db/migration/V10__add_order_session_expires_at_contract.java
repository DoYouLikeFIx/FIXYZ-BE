package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V10__add_order_session_expires_at_contract extends BaseJavaMigration {

  private static final String TABLE_NAME = "order_sessions";
  private static final String COLUMN_NAME = "expires_at";
  private static final String INDEX_NAME = "idx_order_sessions_status_expires_at";
  private static final long ORDER_SESSION_TTL_SECONDS = 600L;

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    if (!columnExists(connection, TABLE_NAME, COLUMN_NAME)) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN expires_at TIMESTAMP");
      }
    }

    backfillMissingExpiresAt(connection);
    applyNotNullConstraint(connection);

    if (!indexExists(connection, TABLE_NAME, INDEX_NAME)) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE INDEX idx_order_sessions_status_expires_at "
            + "ON order_sessions(status, expires_at)");
      }
    }
  }

  private void backfillMissingExpiresAt(Connection connection) throws Exception {
    try (
        PreparedStatement select = connection.prepareStatement(
            "SELECT id, created_at FROM order_sessions WHERE expires_at IS NULL"
        );
        ResultSet resultSet = select.executeQuery();
        PreparedStatement update = connection.prepareStatement(
            "UPDATE order_sessions SET expires_at = ? WHERE id = ?"
        )
      ) {
      while (resultSet.next()) {
        LocalDateTime createdAt = resultSet.getObject("created_at", LocalDateTime.class);
        LocalDateTime expiresAt = createdAt.plusSeconds(ORDER_SESSION_TTL_SECONDS);
        update.setObject(1, expiresAt);
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
        statement.execute("ALTER TABLE order_sessions ALTER COLUMN expires_at SET NOT NULL");
      } else {
        statement.execute("ALTER TABLE order_sessions MODIFY COLUMN expires_at TIMESTAMP NOT NULL");
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
    try (
        ResultSet resultSet = metaData.getColumns(
            connection.getCatalog(),
            null,
            tableName.toUpperCase(Locale.ROOT),
            columnName.toUpperCase(Locale.ROOT)
        )
    ) {
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
    try (
        ResultSet resultSet = metaData.getIndexInfo(
            connection.getCatalog(),
            null,
            tableName.toUpperCase(Locale.ROOT),
            false,
            false
        )
    ) {
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
