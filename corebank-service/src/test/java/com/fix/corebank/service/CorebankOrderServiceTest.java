package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepOutboundOrderPayload;
import com.fix.corebank.entity.Account;
import com.fix.corebank.entity.JournalEntry;
import com.fix.corebank.entity.LedgerEntry;
import com.fix.corebank.entity.LedgerEntryRef;
import com.fix.corebank.entity.Order;
import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.JournalEntryRepository;
import com.fix.corebank.repository.LedgerEntryRefRepository;
import com.fix.corebank.repository.LedgerEntryRepository;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderRequeryCommand;
import com.fix.corebank.vo.InternalOrderResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class CorebankOrderServiceTest {

  private static final Long ACCOUNT_ID = 1001L;
  private static final String IDEMPOTENT_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174220";
  private static final String REQUERY_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174221";
  private static final String PAYLOAD_BOUND_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174222";

  @Mock
  private AccountRepository accountRepository;

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private PositionRepository positionRepository;

  @Mock
  private ExecutionRepository executionRepository;

  @Mock
  private JournalEntryRepository journalEntryRepository;

  @Mock
  private LedgerEntryRepository ledgerEntryRepository;

  @Mock
  private LedgerEntryRefRepository ledgerEntryRefRepository;

  private StubFepClient fepClient;
  private CorebankOrderService corebankOrderService;

  @BeforeEach
  void setUp() {
    fepClient = new StubFepClient();
    corebankOrderService = new CorebankOrderService(
        accountRepository,
        orderRepository,
        positionRepository,
        executionRepository,
        journalEntryRepository,
        ledgerEntryRepository,
        ledgerEntryRefRepository,
        fepClient
    );
  }

  @Test
  void shouldHandleClOrdIdIdempotency() {
    Account account = persistedAccount();
    Position position = Position.of(ACCOUNT_ID, "005930", new BigDecimal("120.0000"), new BigDecimal("70000.0000"));
    Order savedOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            IDEMPOTENT_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("3.0000"),
            new BigDecimal("70200.0000")
        ),
        9001L
    );

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(savedOrder));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));
    when(executionRepository.sumSellQuantityByAccountAndSymbolBetween(eq(ACCOUNT_ID), eq("005930"), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
    when(journalEntryRepository.save(any(JournalEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7001L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8001L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
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

    InternalOrderCreateCommand command = InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    );

    InternalOrderResult first = corebankOrderService.createOrder(command);
    InternalOrderResult second = corebankOrderService.createOrder(command);

    assertThat(first.getOrderId()).isEqualTo(second.getOrderId());
    assertThat(first.isIdempotent()).isFalse();
    assertThat(second.isIdempotent()).isTrue();
    assertThat(first.getStatus()).isEqualTo("FILLED");
    assertThat(fepClient.submitCalls()).isEqualTo(1);
  }

  @Test
  void shouldBindGatewayReferenceIdToLocalClOrdIdOnSubmit() {
    Account account = persistedAccount();
    Position position = Position.of(ACCOUNT_ID, "005930", new BigDecimal("120.0000"), new BigDecimal("70000.0000"));
    Order savedOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            PAYLOAD_BOUND_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("3.0000"),
            new BigDecimal("70200.0000")
        ),
        9003L
    );

    when(orderRepository.findByClOrdId(PAYLOAD_BOUND_CL_ORD_ID)).thenReturn(Optional.empty());
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));
    when(executionRepository.sumSellQuantityByAccountAndSymbolBetween(eq(ACCOUNT_ID), eq("005930"), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
    when(journalEntryRepository.save(any(JournalEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7003L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8003L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    fepClient.setSubmitResult(new FepOrderResult(
        PAYLOAD_BOUND_CL_ORD_ID,
        "FEP-KRX-" + PAYLOAD_BOUND_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        3L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null
    ));

    corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        PAYLOAD_BOUND_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    ));

    assertThat(fepClient.lastSubmitPayload()).isNotNull();
    assertThat(fepClient.lastSubmitPayload().clOrdId()).isEqualTo(PAYLOAD_BOUND_CL_ORD_ID);
    assertThat(fepClient.lastSubmitPayload().referenceId()).isEqualTo(PAYLOAD_BOUND_CL_ORD_ID);
  }

  @Test
  void shouldLockPositionBeforeSubmittingOrder() {
    Account account = persistedAccount();
    Position position = Position.of(ACCOUNT_ID, "005930", new BigDecimal("120.0000"), new BigDecimal("70000.0000"));
    Order savedOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            IDEMPOTENT_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("3.0000"),
            new BigDecimal("70200.0000")
        ),
        9001L
    );

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.empty());
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    final boolean[] lockObserved = {false};
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930"))
        .thenAnswer(invocation -> {
          lockObserved[0] = true;
          return Optional.of(position);
        });
    when(executionRepository.sumSellQuantityByAccountAndSymbolBetween(eq(ACCOUNT_ID), eq("005930"), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
    when(journalEntryRepository.save(any(JournalEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7001L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8001L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    fepClient.onSubmit(() -> assertThat(lockObserved[0]).isTrue());
    fepClient.setSubmitResult(new FepOrderResult(
        IDEMPOTENT_CL_ORD_ID,
        "FEP-KRX-" + IDEMPOTENT_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        3L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null
    ));

    corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    ));

    verify(positionRepository, times(1)).findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930");
    assertThat(fepClient.submitCalls()).isEqualTo(1);
  }

  @Test
  void shouldRequeryOrderStatusThroughFepClient() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9002L
    );
    existingOrder.updateStatus("PENDING");

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
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

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID));

    assertThat(result.getStatus()).isEqualTo("UNKNOWN");
    assertThat(fepClient.queryCalls()).isEqualTo(1);
  }

  private Account persistedAccount() {
    Account account = Account.of(
        "ACC-1001",
        "M-1001",
        "KRW",
        new BigDecimal("100000000.0000"),
        new BigDecimal("500.0000")
    );
    return withId(account, ACCOUNT_ID);
  }

  private Order persistedOrder(Order order, Long id) {
    return withId(order, id);
  }

  private <T> T withId(T target, Long id) {
    ReflectionTestUtils.setField(target, "id", id);
    return target;
  }

  private static final class StubFepClient extends FepClient {

    private FepOrderResult submitResult;
    private FepOrderResult queryResult;
    private FepOutboundOrderPayload lastSubmitPayload;
    private Runnable onSubmit = () -> {
    };
    private int submitCalls;
    private int queryCalls;

    private StubFepClient() {
      super(RestClient.builder().baseUrl("http://localhost").build(), "test-secret");
    }

    @Override
    public FepOrderResult submitOrder(FepOutboundOrderPayload payload, String correlationId) {
      onSubmit.run();
      submitCalls++;
      lastSubmitPayload = payload;
      return submitResult;
    }

    @Override
    public FepOrderResult queryOrderStatus(String clOrdId, String correlationId) {
      queryCalls++;
      return queryResult;
    }

    private void setSubmitResult(FepOrderResult submitResult) {
      this.submitResult = submitResult;
    }

    private void setQueryResult(FepOrderResult queryResult) {
      this.queryResult = queryResult;
    }

    private void onSubmit(Runnable onSubmit) {
      this.onSubmit = onSubmit;
    }

    private FepOutboundOrderPayload lastSubmitPayload() {
      return lastSubmitPayload;
    }

    private int submitCalls() {
      return submitCalls;
    }

    private int queryCalls() {
      return queryCalls;
    }
  }
}
