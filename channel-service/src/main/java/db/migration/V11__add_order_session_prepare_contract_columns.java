package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V11__add_order_session_prepare_contract_columns extends BaseJavaMigration {

  private static final String TABLE_NAME = "order_sessions";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    addColumnIfMissing(connection, "account_id", "BIGINT");
    addColumnIfMissing(connection, "symbol", "VARCHAR(16)");
    addColumnIfMissing(connection, "side", "VARCHAR(16)");
    addColumnIfMissing(connection, "order_type", "VARCHAR(16)");
    addColumnIfMissing(connection, "qty", "DECIMAL(19, 4)");
    addColumnIfMissing(connection, "price", "DECIMAL(19, 4)");
  }

  private void addColumnIfMissing(Connection connection, String columnName, String columnDefinition) throws Exception {
    if (columnExists(connection, TABLE_NAME, columnName)) {
      return;
    }
    try (Statement statement = connection.createStatement()) {
      statement.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + columnName + " " + columnDefinition);
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
}
