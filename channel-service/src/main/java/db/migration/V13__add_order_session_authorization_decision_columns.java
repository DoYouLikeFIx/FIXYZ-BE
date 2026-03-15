package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V13__add_order_session_authorization_decision_columns extends BaseJavaMigration {

  private static final String TABLE_NAME = "order_sessions";
  private static final String CHALLENGE_REQUIRED_COLUMN = "challenge_required";
  private static final String AUTHORIZATION_REASON_COLUMN = "authorization_reason";
  private static final String LEGACY_AUTHORIZATION_REASON = "ELEVATED_ORDER_RISK";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    addColumnIfMissing(connection, CHALLENGE_REQUIRED_COLUMN, "BOOLEAN");
    addColumnIfMissing(connection, AUTHORIZATION_REASON_COLUMN, "VARCHAR(64)");
    backfillLegacyAuthorizationDecision(connection);
    applyNotNullConstraints(connection);
  }

  private void backfillLegacyAuthorizationDecision(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "UPDATE order_sessions SET challenge_required = TRUE WHERE challenge_required IS NULL"
      );
      statement.execute(
          "UPDATE order_sessions SET authorization_reason = '"
              + LEGACY_AUTHORIZATION_REASON
              + "' WHERE authorization_reason IS NULL"
      );
    }
  }

  private void applyNotNullConstraints(Connection connection) throws Exception {
    String databaseName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    try (Statement statement = connection.createStatement()) {
      if (databaseName.contains("h2")) {
        statement.execute("ALTER TABLE order_sessions ALTER COLUMN challenge_required SET NOT NULL");
        statement.execute("ALTER TABLE order_sessions ALTER COLUMN authorization_reason SET NOT NULL");
      } else {
        statement.execute("ALTER TABLE order_sessions MODIFY COLUMN challenge_required BOOLEAN NOT NULL");
        statement.execute("ALTER TABLE order_sessions MODIFY COLUMN authorization_reason VARCHAR(64) NOT NULL");
      }
    }
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
