package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
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
import com.fix.corebank.vo.AccountPositionQueryCommand;
import com.fix.corebank.vo.AccountPositionResult;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderRequeryCommand;
import com.fix.corebank.vo.InternalOrderResult;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class CorebankOrderServiceTest {

  private static final Long ACCOUNT_ID = 1001L;
  private static final Long OWNER_MEMBER_ID = 1001L;
  private static final Long OTHER_MEMBER_ID = 2002L;
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
  private CorebankOrderPersistenceService corebankOrderPersistenceService;
  private CorebankOrderService corebankOrderService;

  @BeforeEach
  void setUp() {
    fepClient = new StubFepClient();
    corebankOrderPersistenceService = new CorebankOrderPersistenceService(
        accountRepository,
        orderRepository,
        positionRepository,
        executionRepository,
        journalEntryRepository,
        ledgerEntryRepository,
        ledgerEntryRefRepository
    );
    corebankOrderService = new CorebankOrderService(
        accountRepository,
        positionRepository,
        executionRepository,
        corebankOrderPersistenceService,
        fepClient
    );
  }

  @Test
  void shouldUseRepeatableReadForAccountPositionConsistency() throws NoSuchMethodException {
    Method method = CorebankOrderService.class.getDeclaredMethod("getAccountPosition", AccountPositionQueryCommand.class);
    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.readOnly()).isTrue();
    assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
  }

  @Test
  void shouldReturnAccountPositionWhenOwnershipMatches() {
    Instant accountUpdatedAt = Instant.parse("2026-03-01T10:00:00Z");
    Instant positionUpdatedAt = Instant.parse("2026-03-01T10:01:00Z");

    Account account = withUpdatedAt(persistedAccount(), accountUpdatedAt);
    Position position = withUpdatedAt(
        Position.of(ACCOUNT_ID, "005930", new BigDecimal("120.0000"), new BigDecimal("70000.0000")),
        positionUpdatedAt
    );

    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbol(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));

    AccountPositionResult result = corebankOrderService.getAccountPosition(
        AccountPositionQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID, "005930")
    );

    assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.getMemberId()).isEqualTo(OWNER_MEMBER_ID);
    assertThat(result.getSymbol()).isEqualTo("005930");
    assertThat(result.getQuantity()).isEqualByComparingTo("120.0000");
    assertThat(result.getAvailableQuantity()).isEqualByComparingTo("120.0000");
    assertThat(result.getBalance()).isEqualByComparingTo("100000000.0000");
    assertThat(result.getCurrency()).isEqualTo("KRW");
    assertThat(result.getAsOf()).isEqualTo(positionUpdatedAt);
  }

  @Test
  void shouldReturnZeroQuantityWhenPositionDoesNotExist() {
    Instant accountUpdatedAt = Instant.parse("2026-03-01T10:00:00Z");
    Account account = withUpdatedAt(persistedAccount(), accountUpdatedAt);

    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbol(ACCOUNT_ID, "000660")).thenReturn(Optional.empty());

    AccountPositionResult result = corebankOrderService.getAccountPosition(
        AccountPositionQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID, "000660")
    );

    assertThat(result.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getAvailableQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getBalance()).isEqualByComparingTo("100000000.0000");
    assertThat(result.getAsOf()).isEqualTo(accountUpdatedAt);
  }

  @Test
  void shouldRejectAccountPositionLookupWhenOwnershipMismatches() {
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccount()));

    assertThatThrownBy(() -> corebankOrderService.getAccountPosition(
        AccountPositionQueryCommand.of(ACCOUNT_ID, OTHER_MEMBER_ID, "005930")
    ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP));
  }

  @Test
  void shouldReturnNotFoundWhenAccountPositionTargetAccountDoesNotExist() {
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> corebankOrderService.getAccountPosition(
        AccountPositionQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID, "005930")
    ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.CORE_RESOURCE_NOT_FOUND));
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
    assertThat(first.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_CONFIRMED);
    assertThat(second.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_CONFIRMED);
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

    when(orderRepository.findByClOrdId(PAYLOAD_BOUND_CL_ORD_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(savedOrder));
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
    assertThat(savedOrder.getFepReferenceId()).isEqualTo("FEP-KRX-" + PAYLOAD_BOUND_CL_ORD_ID);
    assertThat(savedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_CONFIRMED);
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

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(savedOrder));
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
  void shouldPersistExternalSyncFailureWhenSubmitFailsAfterLocalCommit() {
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
        9010L
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
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7010L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8010L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    fepClient.setSubmitFailure(new BusinessException(
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage(),
        new ErrorMetadata("error.fep.timeout", "TIMEOUT")
    ));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    )))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.FEP_GATEWAY_TIMEOUT);

    assertThat(savedOrder.getStatus()).isEqualTo("ACCEPTED");
    assertThat(savedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(savedOrder.getFailureReason()).isEqualTo("TIMEOUT");
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
    assertThat(result.getMessage()).isEqualTo("order not found in exchange");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getRetriable()).isTrue();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(result.getAttemptCount()).isEqualTo(1);
    assertThat(result.getMaxRetryCount()).isEqualTo(5);
    assertThat(fepClient.queryCalls()).isEqualTo(1);
  }

  @Test
  void shouldEscalateUnknownRequeryWhenAttemptThresholdIsReached() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9004L
    );
    existingOrder.updateStatus("UNKNOWN");

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
        Instant.parse("2026-03-01T10:11:00Z"),
        "still unresolved"
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 5));

    assertThat(result.getStatus()).isEqualTo("UNKNOWN");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    assertThat(result.getRetriable()).isFalse();
    assertThat(result.getEscalationRequired()).isTrue();
    assertThat(result.getAttemptCount()).isEqualTo(5);
    assertThat(result.getMaxRetryCount()).isEqualTo(5);
  }

  @Test
  void shouldReturnRetriableMetadataForTransientRequeryTimeout() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9005L
    );
    existingOrder.updateStatus("PENDING");

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.setQueryFailure(new BusinessException(
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage()
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("PENDING");
    assertThat(result.getMessage()).isEqualTo("Exchange connectivity timeout");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getRetriable()).isTrue();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(result.getAttemptCount()).isEqualTo(2);
    assertThat(result.getMaxRetryCount()).isEqualTo(5);
  }

  @Test
  void shouldNotRetryTransientRequeryFailureWhenLocalOrderIsAlreadyTerminal() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9006L
    );
    existingOrder.updateStatus("FILLED");

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.setQueryFailure(new BusinessException(
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage()
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("FILLED");
    assertThat(result.getMessage()).isEqualTo("Exchange connectivity timeout");
    assertThat(result.getExternalSyncStatus()).isNull();
    assertThat(result.getRetriable()).isFalse();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(result.getAttemptCount()).isEqualTo(2);
    assertThat(result.getMaxRetryCount()).isEqualTo(5);
  }

  @Test
  void shouldKeepTransactionalBoundariesInPersistenceServiceOnly() throws Exception {
    Method createOrder = CorebankOrderService.class.getMethod("createOrder", InternalOrderCreateCommand.class);
    Method requeryOrder = CorebankOrderService.class.getMethod("requeryOrder", InternalOrderRequeryCommand.class);
    Method prepareOrderSubmission = CorebankOrderPersistenceService.class.getMethod(
        "prepareOrderSubmission",
        InternalOrderCreateCommand.class
    );
    Method getRequiredOrder = CorebankOrderPersistenceService.class.getMethod("getRequiredOrder", String.class);
    Method updateOrderState = CorebankOrderPersistenceService.class.getMethod(
        "updateOrderState",
        String.class,
        String.class,
        String.class,
        String.class,
        String.class
    );

    assertThat(createOrder.getAnnotation(Transactional.class)).isNull();
    assertThat(requeryOrder.getAnnotation(Transactional.class)).isNull();
    assertThat(prepareOrderSubmission.getAnnotation(Transactional.class)).isNotNull();
    assertThat(getRequiredOrder.getAnnotation(Transactional.class)).isNotNull();
    assertThat(getRequiredOrder.getAnnotation(Transactional.class).readOnly()).isTrue();
    assertThat(updateOrderState.getAnnotation(Transactional.class)).isNotNull();
  }

  private Account persistedAccount() {
    Account account = Account.of(
        "ACC-1001",
        OWNER_MEMBER_ID,
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

  private <T> T withUpdatedAt(T target, Instant updatedAt) {
    ReflectionTestUtils.setField(target, "updatedAt", updatedAt);
    return target;
  }

  private static final class StubFepClient extends FepClient {

    private FepOrderResult submitResult;
    private FepOrderResult queryResult;
    private RuntimeException submitFailure;
    private RuntimeException queryFailure;
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
      if (submitFailure != null) {
        throw submitFailure;
      }
      return submitResult;
    }

    @Override
    public FepOrderResult queryOrderStatus(String clOrdId, String correlationId) {
      queryCalls++;
      if (queryFailure != null) {
        throw queryFailure;
      }
      return queryResult;
    }

    private void setSubmitResult(FepOrderResult submitResult) {
      this.submitResult = submitResult;
    }

    private void setSubmitFailure(RuntimeException submitFailure) {
      this.submitFailure = submitFailure;
    }

    private void setQueryResult(FepOrderResult queryResult) {
      this.queryResult = queryResult;
    }

    private void setQueryFailure(RuntimeException queryFailure) {
      this.queryFailure = queryFailure;
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
