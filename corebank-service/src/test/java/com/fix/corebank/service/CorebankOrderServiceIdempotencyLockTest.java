package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.support.TestStubFepClient;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderRequeryCommand;
import com.fix.corebank.vo.InternalOrderResult;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(CorebankOrderServiceIdempotencyLockTest.StubFepClientConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_idempotency;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class CorebankOrderServiceIdempotencyLockTest {

  private static final String IDEMPOTENT_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174220";
  private static final String REQUERY_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174221";

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private PositionRepository positionRepository;

  @Autowired
  private TestStubFepClient fepClient;

  @BeforeEach
  void setUp() {
    fepClient.reset();
  }

  @Test
  void shouldHandleClOrdIdIdempotency() {
    fepClient.setSubmitResult(new FepOrderResult(
        IDEMPOTENT_CL_ORD_ID,
        "FEP-KRX-" + IDEMPOTENT_CL_ORD_ID,
        FepExecType.FILL,
        FepOrdStatus.FILLED,
        3L,
        70200L,
        0L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null
    ));

    long beforeCount = orderRepository.count();

    InternalOrderCreateCommand command = InternalOrderCreateCommand.of(
        1L,
        IDEMPOTENT_CL_ORD_ID,
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
    assertThat(first.getStatus()).isEqualTo("FILLED");
    assertThat(afterCount).isEqualTo(beforeCount + 1);
    assertThat(fepClient.submitCalls()).isEqualTo(1);
  }

  @Test
  @Transactional
  void shouldAcquirePositionLockUsingPessimisticWrite() {
    assertThat(positionRepository.findByAccountIdAndSymbolForUpdate(1L, "005930")).isPresent();
  }

  @Test
  void shouldRequeryOrderStatusThroughFepClient() {
    fepClient.setSubmitResult(new FepOrderResult(
        REQUERY_CL_ORD_ID,
        "FEP-KRX-" + REQUERY_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        2L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null
    ));
    fepClient.setQueryResult(new FepOrderResult(
        REQUERY_CL_ORD_ID,
        null,
        null,
        FepOrdStatus.UNKNOWN,
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-01T10:10:00Z"),
        "order not found in exchange"
    ));

    corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        1L,
        REQUERY_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID));

    assertThat(result.getStatus()).isEqualTo("UNKNOWN");
    assertThat(fepClient.queryCalls()).isEqualTo(1);
  }

  @TestConfiguration
  static class StubFepClientConfig {

    @Bean
    @Primary
    TestStubFepClient testStubFepClient() {
      return new TestStubFepClient();
    }
  }
}
