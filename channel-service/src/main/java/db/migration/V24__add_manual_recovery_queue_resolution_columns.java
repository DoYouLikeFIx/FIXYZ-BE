package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V24__add_manual_recovery_queue_resolution_columns extends BaseJavaMigration {

  private static final String TABLE_NAME = "manual_recovery_queue_entries";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      if (!columnExists(connection, "resolved_by")) {
        statement.execute("ALTER TABLE manual_recovery_queue_entries ADD COLUMN resolved_by CHAR(36)");
      }
      if (!columnExists(connection, "resolution")) {
        statement.execute("ALTER TABLE manual_recovery_queue_entries ADD COLUMN resolution VARCHAR(32)");
      }
      if (!columnExists(connection, "resolved_at")) {
        statement.execute("ALTER TABLE manual_recovery_queue_entries ADD COLUMN resolved_at TIMESTAMP");
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
