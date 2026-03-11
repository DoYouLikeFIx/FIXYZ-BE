package com.fix.corebank.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class CoreFlywayValidateFailureTest {

  private static final List<String> MIGRATION_FILES = List.of(
      "V1__create_member_table.sql",
      "V2__create_corebank_scaffolding_tables.sql",
      "V3__align_member_accounts_constraints.sql",
      "V4__add_order_external_sync_tracking.sql",
      "V5__add_account_status_events.sql"
  );

  @Test
  void shouldFailFastWhenMigrationChecksumChanges(@TempDir Path tempDir) throws IOException {
    String jdbcUrl = "jdbc:h2:mem:core_validate_failure;MODE=MySQL;DB_CLOSE_DELAY=-1";

    Flyway baseline = Flyway.configure()
        .dataSource(jdbcUrl, "sa", "")
        .locations("classpath:db/migration")
        .load();
    baseline.migrate();

    copyMigrationsTo(tempDir);
    Path v1Path = tempDir.resolve("V1__create_member_table.sql");
    String updatedContent = Files.readString(v1Path, StandardCharsets.UTF_8)
        + System.lineSeparator()
        + "-- checksum mismatch for validate-on-migrate fail-fast test";
    Files.writeString(v1Path, updatedContent, StandardCharsets.UTF_8);

    Flyway mismatch = Flyway.configure()
        .dataSource(jdbcUrl, "sa", "")
        .validateOnMigrate(true)
        .locations("filesystem:" + tempDir.toAbsolutePath())
        .load();

    assertThatThrownBy(mismatch::migrate).isInstanceOf(FlywayValidateException.class);
  }

  private void copyMigrationsTo(Path targetDirectory) throws IOException {
    for (String migrationFile : MIGRATION_FILES) {
      ClassPathResource resource = new ClassPathResource("db/migration/" + migrationFile);
      try (InputStream inputStream = resource.getInputStream()) {
        Files.copy(inputStream, targetDirectory.resolve(migrationFile));
      }
    }
  }
}
