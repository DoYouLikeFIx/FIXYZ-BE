package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V23__add_order_session_quote_context_columns extends BaseJavaMigration {

  private static final String TABLE_NAME = "order_sessions";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      addColumnIfMissing(connection, statement, "quote_snapshot_id", "VARCHAR(128)");
      addColumnIfMissing(connection, statement, "quote_as_of", "TIMESTAMP");
      addColumnIfMissing(connection, statement, "quote_source_mode", "VARCHAR(16)");
      addColumnIfMissing(connection, statement, "pre_trade_price", "DECIMAL(19, 4)");
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
    statement.execute("ALTER TABLE order_sessions ADD COLUMN " + columnName + " " + columnDefinition);
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
