package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V22__ensure_audit_security_uuid_insert_defaults extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    String databaseProductName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);

    try (Statement statement = connection.createStatement()) {
      applyAuditCompatibilityDefaults(connection, statement, databaseProductName);
      applySecurityEventCompatibilityDefaults(connection, statement, databaseProductName);
    }
  }

  private void applyAuditCompatibilityDefaults(
      Connection connection,
      Statement statement,
      String databaseProductName
  ) throws SQLException {
    applyUuidInsertDefault(connection, statement, databaseProductName, "audit_logs", "audit_uuid");
  }

  private void applySecurityEventCompatibilityDefaults(
      Connection connection,
      Statement statement,
      String databaseProductName
  ) throws SQLException {
    applyUuidInsertDefault(connection, statement, databaseProductName, "security_events", "security_event_uuid");
    applyLiteralDefault(connection, statement, databaseProductName, "security_events", "status", "OPEN", "VARCHAR(32)");
    applyTimestampDefault(connection, statement, databaseProductName, "security_events", "occurred_at");
  }

  private void applyUuidInsertDefault(
      Connection connection,
      Statement statement,
      String databaseProductName,
      String tableName,
      String columnName
  ) throws SQLException {
    if (!columnExists(connection, tableName, columnName)) {
      return;
    }

    if (databaseProductName.contains("h2")) {
      statement.execute(
          "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " SET DEFAULT CAST(RANDOM_UUID() AS CHAR(36))"
      );
      return;
    }

    if (databaseProductName.contains("mysql")) {
      statement.execute(
          "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " CHAR(36) NOT NULL DEFAULT (UUID())"
      );
    }
  }

  private void applyLiteralDefault(
      Connection connection,
      Statement statement,
      String databaseProductName,
      String tableName,
      String columnName,
      String literalValue,
      String mysqlColumnDefinition
  ) throws SQLException {
    if (!columnExists(connection, tableName, columnName)) {
      return;
    }

    if (databaseProductName.contains("h2")) {
      statement.execute(
          "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " SET DEFAULT '" + literalValue + "'"
      );
      return;
    }

    if (databaseProductName.contains("mysql")) {
      statement.execute(
          "ALTER TABLE " + tableName + " MODIFY COLUMN "
              + columnName
              + " "
              + mysqlColumnDefinition
              + " NOT NULL DEFAULT '"
              + literalValue
              + "'"
      );
    }
  }

  private void applyTimestampDefault(
      Connection connection,
      Statement statement,
      String databaseProductName,
      String tableName,
      String columnName
  ) throws SQLException {
    if (!columnExists(connection, tableName, columnName)) {
      return;
    }

    if (databaseProductName.contains("h2")) {
      statement.execute(
          "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " SET DEFAULT CURRENT_TIMESTAMP"
      );
      return;
    }

    if (databaseProductName.contains("mysql")) {
      statement.execute(
          "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
      );
    }
  }

  private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
    DatabaseMetaData metadata = connection.getMetaData();
    try (ResultSet resultSet = metadata.getColumns(connection.getCatalog(), null, tableName, columnName)) {
      if (resultSet.next()) {
        return true;
      }
    }
    try (ResultSet resultSet = metadata.getColumns(
        connection.getCatalog(),
        null,
        tableName.toUpperCase(Locale.ROOT),
        columnName.toUpperCase(Locale.ROOT)
    )) {
      return resultSet.next();
    }
  }
}
