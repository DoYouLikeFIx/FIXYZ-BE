package com.fix.corebank.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.PositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_querydsl;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class CorebankQuerydslRepositoryTest {

  @Autowired
  private PositionRepository positionRepository;

  @Autowired
  private ExecutionRepository executionRepository;

  @Test
  void shouldFindPositionByAccountAndSymbolWithQuerydsl() {
    Optional<Position> position = positionRepository.findByAccountIdAndSymbol(1L, "005930");

    assertThat(position).isPresent();
    assertThat(position.get().getQty()).isEqualByComparingTo(new BigDecimal("120.0000"));
  }

  @Test
  void shouldSumDailySellQuantityWithQuerydsl() {
    Instant from = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant to = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

    BigDecimal sum = executionRepository.sumSellQuantityByAccountAndSymbolBetween(1L, "005930", from, to);

    assertThat(sum).isEqualByComparingTo(new BigDecimal("100.0000"));
  }
}
