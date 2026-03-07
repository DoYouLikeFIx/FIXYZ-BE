package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_idempotency;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class CorebankOrderServiceIdempotencyLockTest {

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private PositionRepository positionRepository;

  @Test
  void shouldHandleClOrdIdIdempotency() {
    long beforeCount = orderRepository.count();

    InternalOrderCreateCommand command = InternalOrderCreateCommand.of(
        1L,
        "CORE-IDEMPOTENT-001",
        "005930",
        "BUY",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    );

    InternalOrderResult first = corebankOrderService.createOrder(command);
    InternalOrderResult second = corebankOrderService.createOrder(command);

    long afterCount = orderRepository.count();

    assertThat(first.getOrderId()).isEqualTo(second.getOrderId());
    assertThat(first.isIdempotent()).isFalse();
    assertThat(second.isIdempotent()).isTrue();
    assertThat(afterCount).isEqualTo(beforeCount + 1);
  }

  @Test
  @Transactional
  void shouldAcquirePositionLockUsingPessimisticWrite() {
    assertThat(positionRepository.findByAccountIdAndSymbolForUpdate(1L, "005930")).isPresent();
  }
}
