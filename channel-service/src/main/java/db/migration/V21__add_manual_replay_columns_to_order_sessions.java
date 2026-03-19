package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V21__add_manual_replay_columns_to_order_sessions extends BaseJavaMigration {

  private static final String TABLE_NAME = "order_sessions";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      if (!columnExists(connection, "manual_replay_fingerprint")) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN manual_replay_fingerprint VARCHAR(64)");
      }
      if (!columnExists(connection, "manual_replay_processed_by")) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN manual_replay_processed_by CHAR(36)");
      }
      if (!columnExists(connection, "manual_replay_execution_source")) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN manual_replay_execution_source VARCHAR(32)");
      }
      if (!columnExists(connection, "manual_replay_processed_at")) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN manual_replay_processed_at TIMESTAMP");
      }
    }
  }

  private boolean columnExists(Connection connection, String columnName) throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet resultSet = metaData.getColumns(
        connection.getCatalog(),
        null,
        TABLE_NAME,
        columnName
    )) {
      if (resultSet.next()) {
        return true;
      }
    }
    try (ResultSet resultSet = metaData.getColumns(
        connection.getCatalog(),
        null,
        TABLE_NAME.toUpperCase(Locale.ROOT),
        columnName.toUpperCase(Locale.ROOT)
    )) {
      return resultSet.next();
    }
  }
}
