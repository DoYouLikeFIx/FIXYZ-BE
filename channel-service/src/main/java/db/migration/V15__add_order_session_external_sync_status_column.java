package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V15__add_order_session_external_sync_status_column extends BaseJavaMigration {

  private static final String TABLE_NAME = "order_sessions";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      addColumnIfMissing(connection, statement, "external_sync_status", "VARCHAR(32)");
    }
  }

  private void addColumnIfMissing(
      Connection connection,
      Statement statement,
      String columnName,
      String columnDefinition
  ) throws SQLException {
    if (columnExists(connection, columnName)) {
      return;
    }
    statement.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + columnName + " " + columnDefinition);
  }

  private boolean columnExists(Connection connection, String columnName) throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null, TABLE_NAME, columnName)) {
      if (columns.next()) {
        return true;
      }
    }
    try (ResultSet columns = metaData.getColumns(
        connection.getCatalog(),
        null,
        TABLE_NAME.toUpperCase(Locale.ROOT),
        columnName.toUpperCase(Locale.ROOT)
    )) {
      return columns.next();
    }
  }
}
