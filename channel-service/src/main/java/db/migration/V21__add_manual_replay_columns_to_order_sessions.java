package db.migration;

import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V21__add_manual_replay_columns_to_order_sessions extends BaseJavaMigration {

  private static final String TABLE_NAME = "order_sessions";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      if (!columnExists(context, "manual_replay_fingerprint")) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN manual_replay_fingerprint VARCHAR(64)");
      }
      if (!columnExists(context, "manual_replay_processed_by")) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN manual_replay_processed_by CHAR(36)");
      }
      if (!columnExists(context, "manual_replay_execution_source")) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN manual_replay_execution_source VARCHAR(32)");
      }
      if (!columnExists(context, "manual_replay_processed_at")) {
        statement.execute("ALTER TABLE order_sessions ADD COLUMN manual_replay_processed_at TIMESTAMP");
      }
    }
  }

  private boolean columnExists(Context context, String columnName) throws Exception {
    try (ResultSet resultSet = context.getConnection().getMetaData().getColumns(
        context.getConnection().getCatalog(),
        null,
        TABLE_NAME,
        columnName
    )) {
      return resultSet.next();
    }
  }
}
