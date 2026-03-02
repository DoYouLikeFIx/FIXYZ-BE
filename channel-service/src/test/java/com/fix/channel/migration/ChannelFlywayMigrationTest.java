package com.fix.channel.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class ChannelFlywayMigrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldCreateOrderSessionTableAndSeedData() {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_session", Integer.class);
    assertThat(count).isNotNull();
    assertThat(count).isGreaterThanOrEqualTo(1);
  }
}
