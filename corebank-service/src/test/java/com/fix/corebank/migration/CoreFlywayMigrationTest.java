package com.fix.corebank.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class CoreFlywayMigrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldCreateMemberTableAndSeedData() {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member", Integer.class);
    assertThat(count).isNotNull();
    assertThat(count).isGreaterThanOrEqualTo(1);
  }
}
