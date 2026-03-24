package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepOutboundOrderPayload;
import com.fix.corebank.client.FepQuoteSnapshotClient;
import com.fix.corebank.client.FepQuoteSnapshotResult;
import com.fix.corebank.config.CorebankMarketDataProperties;
import com.fix.corebank.entity.Account;
import com.fix.corebank.entity.JournalEntry;
import com.fix.corebank.entity.LedgerEntry;
import com.fix.corebank.entity.LedgerEntryRef;
import com.fix.corebank.entity.Execution;
import com.fix.corebank.entity.Order;
import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.AccountStatusEventRepository;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.JournalEntryRepository;
import com.fix.corebank.repository.LedgerEntryRefRepository;
import com.fix.corebank.repository.LedgerEntryRepository;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.entity.AccountStatusEvent;
import com.fix.corebank.vo.AccountPositionQueryCommand;
import com.fix.corebank.vo.AccountPositionsQueryCommand;
import com.fix.corebank.vo.AccountPositionResult;
import com.fix.corebank.vo.AccountStatusQueryCommand;
import com.fix.corebank.vo.AccountStatusResult;
import com.fix.corebank.vo.AccountStatusTransitionCommand;
import com.fix.corebank.vo.AccountStatusTransitionResult;
import com.fix.corebank.vo.AccountSummaryQueryCommand;
import com.fix.corebank.vo.AccountOrderHistoryQueryCommand;
import com.fix.corebank.vo.AccountOrderHistoryResult;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderRequeryCommand;
import com.fix.corebank.vo.InternalOrderResult;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  private static final String MARKET_PAYLOAD_BOUND_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174223";
  private static final String MARKET_NO_LIQUIDITY_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174224";
  private static final String MARKET_SWEEP_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174225";
  private static final String MARKET_STALE_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174226";
  private static final String LIMIT_RESTING_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174227";
  private static final String LIMIT_CROSS_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174228";
  private static final String MARKET_PARTIAL_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174229";
  private static final String MARKET_MISSING_SNAPSHOT_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174230";
  private static final String MARKET_MISSING_SOURCE_MODE_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174231";

  @Mock
  private AccountRepository accountRepository;

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private AccountStatusEventRepository accountStatusEventRepository;

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
  private StubFepQuoteSnapshotClient fepQuoteSnapshotClient;
  private CorebankOrderPersistenceService corebankOrderPersistenceService;
  private CorebankAccountPositionQueryService corebankAccountPositionQueryService;
  private CorebankOrderService corebankOrderService;
  private PositionLockMetrics positionLockMetrics;

  @BeforeEach
  void setUp() {
    fepClient = new StubFepClient();
    fepQuoteSnapshotClient = new StubFepQuoteSnapshotClient();
    positionLockMetrics = new PositionLockMetrics(new SimpleMeterRegistry());
    CorebankOppositeBookQueryService oppositeBookQueryService =
        new CorebankOppositeBookQueryService(orderRepository);
    CorebankMatchingEngine matchingEngine = new CorebankMatchingEngine();
    corebankOrderPersistenceService = new CorebankOrderPersistenceService(
        accountRepository,
        accountStatusEventRepository,
        orderRepository,
        positionRepository,
        executionRepository,
        journalEntryRepository,
        ledgerEntryRepository,
        ledgerEntryRefRepository,
        positionLockMetrics,
        oppositeBookQueryService,
        matchingEngine,
        (accountId, symbol) -> {
        },
        (order, account, position) -> {
        }
    );
    corebankAccountPositionQueryService = new CorebankAccountPositionQueryService(accountRepository, positionRepository);
    CorebankMarketDataProperties marketDataProperties = new CorebankMarketDataProperties();
    marketDataProperties.setMaxQuoteAgeMs(5_000L);
    marketDataProperties.setQuoteSourceMode(FepQuoteSourceMode.LIVE);
    lenient().when(accountRepository.findByIdForUpdate(anyLong()))
        .thenAnswer(invocation -> accountRepository.findById(invocation.getArgument(0)));
    lenient().when(accountRepository.existsById(anyLong()))
        .thenAnswer(invocation -> accountRepository.findById(invocation.getArgument(0)).isPresent());
    corebankOrderService = new CorebankOrderService(
        accountRepository,
        positionRepository,
        executionRepository,
        corebankOrderPersistenceService,
        fepClient,
        fepQuoteSnapshotClient,
        corebankAccountPositionQueryService,
        new QuoteFreshnessPolicy(
            marketDataProperties,
            Clock.fixed(Instant.parse("2026-03-01T10:02:00Z"), ZoneId.of("UTC"))
        ),
        marketDataProperties,
        positionLockMetrics
    );
    fepQuoteSnapshotClient.setQuoteResult("005930", quoteSnapshot(
        "qsnap-005930-1",
        "005930",
        Instant.parse("2026-03-01T10:01:59Z"),
        72000L,
        72100L,
        72050L
    ));
    fepQuoteSnapshotClient.setQuoteResult("000660", quoteSnapshot(
        "qsnap-000660-1",
        "000660",
        Instant.parse("2026-03-01T10:01:58Z"),
        120000L,
        120500L,
        120250L
    ));
    ReflectionTestUtils.setField(corebankOrderService, "statusQueryMaxAttempts", 2);
    ReflectionTestUtils.setField(corebankOrderService, "statusQueryBackoffMs", 0L);
    org.mockito.Mockito.lenient().when(orderRepository.updateStateIfVersionMatches(
        any(String.class),
        any(Long.class),
        any(String.class),
        any(),
        any(),
        any(),
        any(Instant.class)
    )).thenAnswer(invocation -> {
      String clOrdId = invocation.getArgument(0);
      Long expectedVersion = invocation.getArgument(1);
      String status = invocation.getArgument(2);
      String externalSyncStatus = invocation.getArgument(3);
      String fepReferenceId = invocation.getArgument(4);
      String failureReason = invocation.getArgument(5);
      Instant updatedAt = invocation.getArgument(6);
      Optional<Order> orderOptional = orderRepository.findByClOrdId(clOrdId);
      if (orderOptional == null || orderOptional.isEmpty()) {
        return 0;
      }
      Order order = orderOptional.get();
      if (!java.util.Objects.equals(expectedVersion, order.getVersion())) {
        return 0;
      }
      order.updateState(status, externalSyncStatus, fepReferenceId, failureReason);
      ReflectionTestUtils.setField(order, "updatedAt", updatedAt);
      ReflectionTestUtils.setField(order, "version", order.getVersion() + 1L);
      return 1;
    });
  }

  @Test
  void shouldRejectNonPositiveStatusQueryMaxAttemptsConfiguration() {
    ReflectionTestUtils.setField(corebankOrderService, "statusQueryMaxAttempts", 0);

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(corebankOrderService, "validateRecoveryConfiguration"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("recovery.status-query.max-attempts must be >= 1");
  }

  @Test
  void shouldRejectNegativeStatusQueryBackoffConfiguration() {
    ReflectionTestUtils.setField(corebankOrderService, "statusQueryBackoffMs", -1L);

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(corebankOrderService, "validateRecoveryConfiguration"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("recovery.status-query.backoff-ms must be >= 0");
  }

  @Test
  void shouldUseRepeatableReadForAccountPositionConsistency() throws NoSuchMethodException {
    Method method = CorebankAccountPositionQueryService.class.getDeclaredMethod(
        "getOwnedAccountPosition",
        AccountPositionQueryCommand.class
    );
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
    assertThat(result.getMarketPrice()).isEqualByComparingTo("72050.0000");
    assertThat(result.getQuoteSnapshotId()).isEqualTo("qsnap-005930-1");
    assertThat(result.getQuoteAsOf()).isEqualTo(Instant.parse("2026-03-01T10:01:59Z"));
    assertThat(result.getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
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
    assertThat(result.getMarketPrice()).isEqualByComparingTo("120250.0000");
    assertThat(result.getQuoteSnapshotId()).isEqualTo("qsnap-000660-1");
    assertThat(result.getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
  }

  @Test
  void shouldReturnOwnedAccountPositionsWhenOwnershipMatches() {
    Instant accountUpdatedAt = Instant.parse("2026-03-01T10:00:00Z");
    Instant samsungUpdatedAt = Instant.parse("2026-03-01T10:01:00Z");
    Instant hynixUpdatedAt = Instant.parse("2026-03-01T10:02:00Z");
    Account account = withUpdatedAt(persistedAccount(), accountUpdatedAt);
    Position samsung = withUpdatedAt(
        Position.of(ACCOUNT_ID, "005930", new BigDecimal("120.0000"), new BigDecimal("70000.0000")),
        samsungUpdatedAt
    );
    Position hynix = withUpdatedAt(
        Position.of(ACCOUNT_ID, "000660", new BigDecimal("40.0000"), new BigDecimal("120000.0000")),
        hynixUpdatedAt
    );

    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findAllByAccountIdAndQtyGreaterThanOrderBySymbolAsc(
        ACCOUNT_ID,
        BigDecimal.ZERO
    )).thenReturn(List.of(hynix, samsung));

    List<AccountPositionResult> result = corebankOrderService.getAccountPositions(
        AccountPositionsQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID)
    );

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getSymbol()).isEqualTo("000660");
    assertThat(result.get(0).getQuantity()).isEqualByComparingTo("40.0000");
    assertThat(result.get(0).getBalance()).isEqualByComparingTo("100000000.0000");
    assertThat(result.get(0).getAsOf()).isEqualTo(hynixUpdatedAt);
    assertThat(result.get(0).getMarketPrice()).isEqualByComparingTo("120250.0000");
    assertThat(result.get(0).getQuoteSnapshotId()).isEqualTo("qsnap-000660-1");
    assertThat(result.get(1).getSymbol()).isEqualTo("005930");
    assertThat(result.get(1).getQuantity()).isEqualByComparingTo("120.0000");
    assertThat(result.get(1).getAsOf()).isEqualTo(samsungUpdatedAt);
    assertThat(result.get(1).getMarketPrice()).isEqualByComparingTo("72050.0000");
    assertThat(result.get(1).getQuoteSnapshotId()).isEqualTo("qsnap-005930-1");
    assertThat(fepQuoteSnapshotClient.singleQueryCalls()).isZero();
    assertThat(fepQuoteSnapshotClient.batchQueryCalls()).isEqualTo(1);
  }

  @Test
  void shouldReturnAccountSummaryForCashOnlyAccount() {
    Instant accountUpdatedAt = Instant.parse("2026-03-01T10:00:00Z");
    Account account = withUpdatedAt(persistedAccount(), accountUpdatedAt);

    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

    AccountPositionResult result = corebankOrderService.getAccountSummary(
        AccountSummaryQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID)
    );

    assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.getMemberId()).isEqualTo(OWNER_MEMBER_ID);
    assertThat(result.getSymbol()).isEmpty();
    assertThat(result.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getAvailableQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getBalance()).isEqualByComparingTo("100000000.0000");
    assertThat(result.getCurrency()).isEqualTo("KRW");
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
  void shouldAllowAccountPositionWhenQuoteSnapshotAgeMatchesThresholdExactly() {
    Instant accountUpdatedAt = Instant.parse("2026-03-01T10:00:00Z");
    Account account = withUpdatedAt(persistedAccount(), accountUpdatedAt);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    fepQuoteSnapshotClient.setQuoteResult("005930", quoteSnapshot(
        "qsnap-005930-threshold",
        "005930",
        Instant.parse("2026-03-01T10:01:55Z"),
        72000L,
        72100L,
        72050L
    ));

    AccountPositionResult result = corebankOrderService.getAccountPosition(
        AccountPositionQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID, "005930")
    );

    assertThat(result.getMarketPrice()).isEqualByComparingTo("72050.0000");
    assertThat(result.getQuoteSnapshotId()).isEqualTo("qsnap-005930-threshold");
    assertThat(result.getQuoteAsOf()).isEqualTo(Instant.parse("2026-03-01T10:01:55Z"));
    assertThat(result.getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
  }

  @Test
  void shouldRejectAccountPositionWhenQuoteSnapshotAgeExceedsThresholdByOneMillisecond() {
    Instant accountUpdatedAt = Instant.parse("2026-03-01T10:00:00Z");
    Account account = withUpdatedAt(persistedAccount(), accountUpdatedAt);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    fepQuoteSnapshotClient.setQuoteResult("005930", quoteSnapshot(
        "qsnap-005930-threshold-over",
        "005930",
        Instant.parse("2026-03-01T10:01:54.999Z"),
        72000L,
        72100L,
        72050L
    ));

    assertThatThrownBy(() -> corebankOrderService.getAccountPosition(
        AccountPositionQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID, "005930")
    ))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STALE_QUOTE);
          assertThat(ex.getDetails()).containsEntry("symbol", "005930");
          assertThat(ex.getDetails()).containsEntry("snapshotAgeMs", 5_001L);
          assertThat(ex.getDetails()).containsEntry("quoteSourceMode", "LIVE");
          assertThat(ex.getDetails()).containsEntry("quoteSnapshotId", "qsnap-005930-threshold-over");
        });
  }

  @Test
  void shouldRejectAccountPositionWhenQuoteSnapshotIsStale() {
    Instant accountUpdatedAt = Instant.parse("2026-03-01T10:00:00Z");
    Account account = withUpdatedAt(persistedAccount(), accountUpdatedAt);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    fepQuoteSnapshotClient.setQuoteResult("005930", quoteSnapshot(
        "qsnap-005930-stale",
        "005930",
        Instant.parse("2026-03-01T10:01:54Z"),
        72000L,
        72100L,
        72050L
    ));

    assertThatThrownBy(() -> corebankOrderService.getAccountPosition(
        AccountPositionQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID, "005930")
    ))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STALE_QUOTE);
          assertThat(ex.getDetails()).containsEntry("symbol", "005930");
          assertThat(ex.getDetails()).containsEntry("snapshotAgeMs", 6_000L);
          assertThat(ex.getDetails()).containsEntry("quoteSourceMode", "LIVE");
        });
  }

  @Test
  void shouldRejectAccountPositionWhenQuoteSnapshotIsMissing() {
    Instant accountUpdatedAt = Instant.parse("2026-03-01T10:00:00Z");
    Account account = withUpdatedAt(persistedAccount(), accountUpdatedAt);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    fepQuoteSnapshotClient.setQuoteFailure("005930", new BusinessException(ErrorCode.NOT_FOUND, "quote snapshot not found"));

    assertThatThrownBy(() -> corebankOrderService.getAccountPosition(
        AccountPositionQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID, "005930")
    ))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STALE_QUOTE);
          assertThat(ex.getDetails()).containsEntry("symbol", "005930");
          assertThat(ex.getDetails()).containsEntry("reason", "QUOTE_SNAPSHOT_NOT_FOUND");
        });
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
  void shouldReturnActiveAccountStatusWhenOwnershipMatches() {
    Account account = withUpdatedAt(persistedAccount(), Instant.parse("2026-03-01T10:00:00Z"));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

    AccountStatusResult result = corebankOrderService.getAccountStatus(
        AccountStatusQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID)
    );

    assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.getMemberId()).isEqualTo(OWNER_MEMBER_ID);
    assertThat(result.getAccountNumber()).isEqualTo("ACC-1001");
    assertThat(result.getStatus()).isEqualTo("ACTIVE");
    assertThat(result.isOrderEligible()).isTrue();
    assertThat(result.getDenialCode()).isNull();
    assertThat(result.getAsOf()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
  }

  @Test
  void shouldReturnOrd012WhenAccountStatusBlocksOrderEligibility() {
    Account frozenAccount = Account.of(
        "ACC-1002",
        OWNER_MEMBER_ID,
        "FROZEN",
        "KRW",
        new BigDecimal("100000000.0000"),
        new BigDecimal("500.0000")
    );
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(withId(frozenAccount, ACCOUNT_ID)));

    AccountStatusResult result = corebankOrderService.getAccountStatus(
        AccountStatusQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID)
    );

    assertThat(result.getStatus()).isEqualTo("FROZEN");
    assertThat(result.isOrderEligible()).isFalse();
    assertThat(result.getDenialCode()).isEqualTo("ORD-012");
  }

  @Test
  void shouldRejectAccountStatusLookupWhenOwnershipMismatches() {
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccount()));

    assertThatThrownBy(() -> corebankOrderService.getAccountStatus(
        AccountStatusQueryCommand.of(ACCOUNT_ID, OTHER_MEMBER_ID)
    ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP));
  }

  @Test
  void shouldTransitionAccountStatusAndEmitEvent() {
    Account account = withUpdatedAt(persistedAccountWithStatus("ACTIVE"), Instant.parse("2026-03-01T10:02:00Z"));
    withId(account, ACCOUNT_ID);

    when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(accountStatusEventRepository.save(any(AccountStatusEvent.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7001L));

    AccountStatusTransitionResult result = corebankOrderService.transitionAccountStatus(
        AccountStatusTransitionCommand.of(
            ACCOUNT_ID,
            OWNER_MEMBER_ID,
            "FROZEN",
            "risk-control",
            "ops-admin",
            "ticket=FIX-43",
            "trace-status-transition"
        )
    );

    assertThat(result.getPreviousStatus()).isEqualTo("ACTIVE");
    assertThat(result.getNewStatus()).isEqualTo("FROZEN");
    assertThat(result.isChanged()).isTrue();
    assertThat(result.getEventId()).isEqualTo(7001L);
    assertThat(result.getReason()).isEqualTo("risk-control");
    assertThat(result.getActor()).isEqualTo("ops-admin");
    assertThat(account.getStatus()).isEqualTo("FROZEN");
    verify(accountRepository, times(1)).flush();
    verify(accountStatusEventRepository, times(1)).save(any(AccountStatusEvent.class));
  }

  @Test
  void shouldNotEmitEventWhenStatusTransitionIsNoop() {
    Account account = withUpdatedAt(persistedAccountWithStatus("ACTIVE"), Instant.parse("2026-03-01T10:02:00Z"));
    withId(account, ACCOUNT_ID);
    when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

    AccountStatusTransitionResult result = corebankOrderService.transitionAccountStatus(
        AccountStatusTransitionCommand.of(
            ACCOUNT_ID,
            OWNER_MEMBER_ID,
            "ACTIVE",
            "manual-check",
            "ops-admin",
            null,
            "trace-status-noop"
        )
    );

    assertThat(result.getPreviousStatus()).isEqualTo("ACTIVE");
    assertThat(result.getNewStatus()).isEqualTo("ACTIVE");
    assertThat(result.isChanged()).isFalse();
    assertThat(result.getEventId()).isNull();
    verify(accountRepository, times(0)).flush();
    verify(accountStatusEventRepository, times(0)).save(any(AccountStatusEvent.class));
  }

  @Test
  void shouldReturnOrderHistoryWhenOwnershipMatches() {
    Instant newest = Instant.parse("2026-03-01T10:02:00Z");
    Instant older = Instant.parse("2026-03-01T10:01:00Z");
    Account account = persistedAccount();
    Order newestOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            "123e4567-e89b-42d3-a456-426614174230",
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9010L
    ), newest);
    Order olderOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            "123e4567-e89b-42d3-a456-426614174231",
            "000660",
            "SELL",
            new BigDecimal("1.0000"),
            new BigDecimal("120000.0000")
        ),
        9011L
    ), older);
    PageRequest pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(orderRepository.findByAccountId(ACCOUNT_ID, pageable))
        .thenReturn(new PageImpl<>(List.of(newestOrder, olderOrder), pageable, 2));

    AccountOrderHistoryResult result = corebankOrderService.getAccountOrderHistory(
        AccountOrderHistoryQueryCommand.of(ACCOUNT_ID, OWNER_MEMBER_ID, 0, 2)
    );

    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent().get(0).getClOrdId()).isEqualTo("123e4567-e89b-42d3-a456-426614174230");
    assertThat(result.getContent().get(0).getSymbol()).isEqualTo("005930");
    assertThat(result.getContent().get(0).getSymbolName()).isEqualTo("삼성전자");
    assertThat(result.getContent().get(0).getQty()).isEqualByComparingTo("2.0000");
    assertThat(result.getContent().get(0).getUnitPrice()).isEqualByComparingTo("70100.0000");
    assertThat(result.getContent().get(0).getTotalAmount()).isEqualByComparingTo("140200.00000000");
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.getNumber()).isEqualTo(0);
    assertThat(result.getSize()).isEqualTo(2);
  }

  @Test
  void shouldRejectOrderHistoryLookupWhenOwnershipMismatches() {
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccount()));

    assertThatThrownBy(() -> corebankOrderService.getAccountOrderHistory(
        AccountOrderHistoryQueryCommand.of(ACCOUNT_ID, OTHER_MEMBER_ID, 0, 20)
    ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP));
  }

  @Test
  void shouldRejectOrderCreationWhenAccountIsFrozen() {
    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.empty());
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccountWithStatus("FROZEN")));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.ORD_ACCOUNT_STATUS_BLOCKED);
          assertThat(businessException.getMessage()).contains("FROZEN");
        });
    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldRejectOrderCreationWhenAccountIsClosed() {
    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.empty());
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccountWithStatus("CLOSED")));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "SELL",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.ORD_ACCOUNT_STATUS_BLOCKED);
          assertThat(businessException.getMessage()).contains("CLOSED");
        });
    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldRejectSellOrderWhenRequestedQuantityExceedsAvailablePosition() {
    Account account = persistedAccount();
    Position position = Position.of(ACCOUNT_ID, "005930", new BigDecimal("2.0000"), new BigDecimal("70000.0000"));

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.empty());
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "SELL",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.ORD_INSUFFICIENT_POSITION);
          assertThat(businessException.getDetails()).containsEntry("availableQty", new BigDecimal("2.0000"));
          assertThat(businessException.getDetails()).containsEntry("requestedQty", new BigDecimal("3.0000"));
        });

    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldRejectSellOrderWhenDailyLimitIsExceeded() {
    Account account = persistedAccount();
    Position position = Position.of(ACCOUNT_ID, "005930", new BigDecimal("120.0000"), new BigDecimal("70000.0000"));

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.empty());
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));
    when(executionRepository.sumSellQuantityByAccountAndSymbolBetween(eq(ACCOUNT_ID), eq("005930"), any(), any()))
        .thenReturn(new BigDecimal("480.0000"));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "SELL",
        new BigDecimal("50.0000"),
        new BigDecimal("70200.0000")
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.ORD_DAILY_SELL_LIMIT_EXCEEDED);
          assertThat(businessException.getDetails()).containsEntry("todaySold", new BigDecimal("480.0000"));
          assertThat(businessException.getDetails()).containsEntry("dailyLimit", new BigDecimal("500.0000"));
          assertThat(businessException.getDetails()).containsEntry("remainingLimit", new BigDecimal("20.0000"));
        });

    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldAcceptSellOrderExactlyAtDailyLimitBoundary() {
    Account account = persistedAccount();
    Position position = Position.of(ACCOUNT_ID, "005930", new BigDecimal("120.0000"), new BigDecimal("70000.0000"));
    Order savedOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            IDEMPOTENT_CL_ORD_ID,
            "005930",
            "SELL",
            new BigDecimal("20.0000"),
            new BigDecimal("70200.0000")
        ),
        9012L
    );

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(savedOrder));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));
    when(executionRepository.sumSellQuantityByAccountAndSymbolBetween(eq(ACCOUNT_ID), eq("005930"), any(), any()))
        .thenReturn(new BigDecimal("480.0000"));
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
    fepClient.setSubmitResult(new FepOrderResult(
        IDEMPOTENT_CL_ORD_ID,
        "FEP-KRX-" + IDEMPOTENT_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        20L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null,
        null,
        null
    ));

    InternalOrderResult result = corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "SELL",
        new BigDecimal("20.0000"),
        new BigDecimal("70200.0000")
    ));

    assertThat(result.getStatus()).isEqualTo("NEW");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getExternalOrderId()).isEqualTo("FEP-KRX-" + IDEMPOTENT_CL_ORD_ID);
    assertThat(fepClient.submitCalls()).isEqualTo(1);
  }

  @Test
  void shouldComputeDailyWindowByConfiguredTimezoneRule() {
    Account account = persistedAccount();
    Position position = Position.of(ACCOUNT_ID, "005930", new BigDecimal("120.0000"), new BigDecimal("70000.0000"));
    Order savedOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            IDEMPOTENT_CL_ORD_ID,
            "005930",
            "SELL",
            new BigDecimal("3.0000"),
            new BigDecimal("70200.0000")
        ),
        9013L
    );
    ReflectionTestUtils.setField(
        corebankOrderPersistenceService,
        "limitWindowClock",
        Clock.fixed(Instant.parse("2026-03-01T15:10:00Z"), ZoneId.of("UTC"))
    );
    ReflectionTestUtils.setField(corebankOrderPersistenceService, "limitWindowZone", "Asia/Seoul");

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(savedOrder));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));
    when(executionRepository.sumSellQuantityByAccountAndSymbolBetween(eq(ACCOUNT_ID), eq("005930"), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
    fepClient.setSubmitResult(new FepOrderResult(
        IDEMPOTENT_CL_ORD_ID,
        "FEP-KRX-" + IDEMPOTENT_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        3L,
        Instant.parse("2026-03-01T15:10:30Z"),
        null,
        null,
        null,
        null,
        null
    ));

    corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "SELL",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    ));

    ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(executionRepository, times(1))
        .sumSellQuantityByAccountAndSymbolBetween(eq(ACCOUNT_ID), eq("005930"), fromCaptor.capture(), toCaptor.capture());
    assertThat(fromCaptor.getValue()).isEqualTo(Instant.parse("2026-03-01T15:00:00Z"));
    assertThat(toCaptor.getValue()).isEqualTo(Instant.parse("2026-03-02T15:00:00Z"));
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
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
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
        null,
        null,
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
  void shouldExposeOrderSnapshotForCanonicalClOrdId() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9011L
    );
    existingOrder.updateState(
        "FILLED",
        Order.EXTERNAL_SYNC_CONFIRMED,
        "FEP-KRX-" + REQUERY_CL_ORD_ID,
        null
    );

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));

    var snapshot = corebankOrderService.getOrderSnapshot(REQUERY_CL_ORD_ID);

    assertThat(snapshot.getOrderId()).isEqualTo(9011L);
    assertThat(snapshot.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(snapshot.getClOrdId()).isEqualTo(REQUERY_CL_ORD_ID);
    assertThat(snapshot.getStatus()).isEqualTo("FILLED");
    assertThat(snapshot.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_CONFIRMED);
    assertThat(snapshot.getExternalOrderId()).isEqualTo("FEP-KRX-" + REQUERY_CL_ORD_ID);
  }

  @Test
  void shouldRejectIdempotentReplayWhenAccountDiffers() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            IDEMPOTENT_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("3.0000"),
            new BigDecimal("70200.0000")
        ),
        9002L
    );
    existingOrder.completeExecution(
        "FILLED",
        "FILLED",
        new BigDecimal("3.0000"),
        new BigDecimal("0.0000"),
        new BigDecimal("70200.0000"),
        Instant.parse("2026-03-01T10:05:30Z")
    );
    existingOrder.updateState("FILLED", Order.EXTERNAL_SYNC_CONFIRMED, "FEP-KRX-" + IDEMPOTENT_CL_ORD_ID, null);

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        2002L,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP));

    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldRejectIdempotentReplayWhenPayloadDiffers() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            IDEMPOTENT_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("3.0000"),
            new BigDecimal("70200.0000")
        ),
        9004L
    );
    existingOrder.completeExecution(
        "FILLED",
        "FILLED",
        new BigDecimal("3.0000"),
        new BigDecimal("0.0000"),
        new BigDecimal("70200.0000"),
        Instant.parse("2026-03-01T10:05:30Z")
    );
    existingOrder.updateState("FILLED", Order.EXTERNAL_SYNC_CONFIRMED, "FEP-KRX-" + IDEMPOTENT_CL_ORD_ID, null);

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("4.0000"),
        new BigDecimal("70200.0000")
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.ORD_INVALID_REQUEST));

    assertThat(fepClient.submitCalls()).isZero();
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
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
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
        null,
        null,
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
    assertThat(savedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
  }

  @Test
  void shouldKeepLimitOrderRestingWhenNoPriceCrossExists() {
    Account account = persistedAccount();
    Position position = Position.of(ACCOUNT_ID, "005930", BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4));
    Order makerOrder = persistedOrder(
        Order.accepted(
            2001L,
            "maker-sell-resting",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("2.0000"),
            new BigDecimal("70300.0000"),
            null,
            null,
            null,
            null
        ),
        9100L
    );
    Order[] savedOrderRef = new Order[1];

    when(orderRepository.findByClOrdId(LIMIT_RESTING_CL_ORD_ID))
        .thenAnswer(invocation -> Optional.ofNullable(savedOrderRef[0]));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));
    when(orderRepository.lockExecutionRestingLimitOrdersForSweep(eq("005930"), eq("SELL"), any()))
        .thenReturn(List.of(makerOrder));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> {
          Order persisted = persistedOrder(invocation.getArgument(0), 9016L);
          savedOrderRef[0] = persisted;
          return persisted;
        });
    fepClient.setSubmitResult(new FepOrderResult(
        LIMIT_RESTING_CL_ORD_ID,
        "FEP-KRX-" + LIMIT_RESTING_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        3L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null,
        null,
        null
    ));

    InternalOrderResult result = corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        LIMIT_RESTING_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    ));

    assertThat(result.getStatus()).isEqualTo("NEW");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getExecutionResult()).isNull();
    assertThat(result.getExecutedQty()).isEqualByComparingTo("0.0000");
    assertThat(result.getLeavesQty()).isEqualByComparingTo("3.0000");
    assertThat(result.getExecutedPrice()).isNull();
    assertThat(savedOrderRef[0]).isNotNull();
    assertThat(savedOrderRef[0].getOrderType()).isEqualTo("LIMIT");
    assertThat(savedOrderRef[0].getOrderPrice()).isEqualByComparingTo("70200.0000");
    assertThat(savedOrderRef[0].getStatus()).isEqualTo("NEW");
    assertThat(savedOrderRef[0].getLeavesQty()).isEqualByComparingTo("3.0000");
    assertThat(savedOrderRef[0].getExecutionResult()).isNull();
    assertThat(position.getQty()).isEqualByComparingTo("0.0000");
    assertThat(makerOrder.getStatus()).isEqualTo("NEW");
    assertThat(makerOrder.getExecutedQty()).isNull();
    assertThat(makerOrder.getLeavesQty()).isNull();
    assertThat(fepClient.lastSubmitPayload()).isNotNull();
    assertThat(fepClient.lastSubmitPayload().orderType()).isEqualTo(com.fix.common.fep.FepOrderType.LIMIT);
    assertThat(fepClient.lastSubmitPayload().price()).isEqualTo(70200L);
    verify(journalEntryRepository, times(0)).save(any(JournalEntry.class));
    verify(ledgerEntryRepository, times(0)).save(any(LedgerEntry.class));
    verify(ledgerEntryRefRepository, times(0)).save(any(LedgerEntryRef.class));
  }

  @Test
  void shouldMatchLimitBuyAcrossOppositeBookInStrictPriceTimeOrder() {
    Account takerAccount = persistedAccount();
    Position takerPosition = Position.of(ACCOUNT_ID, "005930", BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4));
    Account makerOneAccount = withId(
        Account.of("ACC-2001", 2001L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        2001L
    );
    Account makerTwoAccount = withId(
        Account.of("ACC-2002", 2002L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        2002L
    );
    Position makerOnePosition = Position.of(2001L, "005930", new BigDecimal("2.0000"), new BigDecimal("68000.0000"));
    Position makerTwoPosition = Position.of(2002L, "005930", new BigDecimal("3.0000"), new BigDecimal("69000.0000"));
    Order makerOneOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            2001L,
            "maker-sell-limit-2001",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("2.0000"),
            new BigDecimal("70000.0000"),
            null,
            null,
            null,
            null
        ),
        9301L
    ), Instant.parse("2026-03-01T09:59:00Z"));
    Order makerTwoOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            2002L,
            "maker-sell-limit-2002",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("3.0000"),
            new BigDecimal("70100.0000"),
            null,
            null,
            null,
            null
        ),
        9302L
    ), Instant.parse("2026-03-01T10:00:00Z"));
    Order[] savedOrderRef = new Order[1];

    when(orderRepository.findByClOrdId(LIMIT_CROSS_CL_ORD_ID))
        .thenAnswer(invocation -> Optional.ofNullable(savedOrderRef[0]));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(takerAccount));
    when(accountRepository.findById(2001L)).thenReturn(Optional.of(makerOneAccount));
    when(accountRepository.findById(2002L)).thenReturn(Optional.of(makerTwoAccount));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(takerPosition));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(2001L, "005930")).thenReturn(Optional.of(makerOnePosition));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(2002L, "005930")).thenReturn(Optional.of(makerTwoPosition));
    when(orderRepository.lockExecutionRestingLimitOrdersForSweep(eq("005930"), eq("SELL"), any()))
        .thenReturn(List.of(makerOneOrder, makerTwoOrder));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> {
          Order persisted = persistedOrder(invocation.getArgument(0), 9018L);
          savedOrderRef[0] = persisted;
          return persisted;
        });
    when(journalEntryRepository.save(any(JournalEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7018L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8018L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    fepClient.setSubmitResult(new FepOrderResult(
        LIMIT_CROSS_CL_ORD_ID,
        "FEP-KRX-" + LIMIT_CROSS_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        4L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null,
        null,
        null
    ));

    InternalOrderResult result = corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        LIMIT_CROSS_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("4.0000"),
        new BigDecimal("70100.0000")
    ));

    assertThat(result.getStatus()).isEqualTo("FILLED");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("4.0000");
    assertThat(result.getLeavesQty()).isEqualByComparingTo("0.0000");
    assertThat(result.getExecutedPrice()).isEqualByComparingTo("70050.0000");
    assertThat(savedOrderRef[0]).isNotNull();
    assertThat(savedOrderRef[0].getOrderType()).isEqualTo("LIMIT");
    assertThat(savedOrderRef[0].getOrderPrice()).isEqualByComparingTo("70100.0000");
    assertThat(savedOrderRef[0].getStatus()).isEqualTo("FILLED");
    assertThat(savedOrderRef[0].getQuoteSnapshotId()).isNull();
    assertThat(savedOrderRef[0].getQuoteAsOf()).isNull();
    assertThat(savedOrderRef[0].getQuoteSourceMode()).isNull();
    assertThat(takerPosition.getQty()).isEqualByComparingTo("4.0000");
    assertThat(takerPosition.getAvgPrice()).isEqualByComparingTo("70050.0000");
    assertThat(makerOneOrder.getStatus()).isEqualTo("FILLED");
    assertThat(makerOneOrder.getExecutedQty()).isEqualByComparingTo("2.0000");
    assertThat(makerOneOrder.getLeavesQty()).isEqualByComparingTo("0.0000");
    assertThat(makerTwoOrder.getStatus()).isEqualTo("PARTIALLY_FILLED");
    assertThat(makerTwoOrder.getExecutedQty()).isEqualByComparingTo("2.0000");
    assertThat(makerTwoOrder.getLeavesQty()).isEqualByComparingTo("1.0000");
    assertThat(fepClient.lastSubmitPayload()).isNotNull();
    assertThat(fepClient.lastSubmitPayload().orderType()).isEqualTo(com.fix.common.fep.FepOrderType.LIMIT);
    assertThat(fepClient.lastSubmitPayload().price()).isEqualTo(70100L);

    ArgumentCaptor<Execution> executionCaptor = ArgumentCaptor.forClass(Execution.class);
    verify(executionRepository, times(4)).saveAndFlush(executionCaptor.capture());
    List<Execution> persistedExecutions = executionCaptor.getAllValues();
    assertThat(executionSeqsForOrder(persistedExecutions, savedOrderRef[0].getId())).containsExactly(1, 2);
    assertThat(executionSeqsForOrder(persistedExecutions, makerOneOrder.getId())).containsExactly(1);
    assertThat(executionSeqsForOrder(persistedExecutions, makerTwoOrder.getId())).containsExactly(1);
    assertThat(persistedExecutions).allSatisfy(execution -> {
      assertThat(execution.getQuoteSnapshotId()).isNull();
      assertThat(execution.getQuoteAsOf()).isNull();
      assertThat(execution.getQuoteSourceMode()).isNull();
    });
  }

  @Test
  void shouldForwardMarketQuoteContextToGatewayPayload() {
    Account account = persistedAccount();
    Position position = Position.of(ACCOUNT_ID, "005930", BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4));
    Account makerAccount = withId(
        Account.of("ACC-2001", 2001L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        2001L
    );
    Position makerPosition = Position.of(2001L, "005930", new BigDecimal("3.0000"), new BigDecimal("70000.0000"));
    Order makerOrder = persistedOrder(
        Order.accepted(
            2001L,
            "maker-sell-1",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("3.0000"),
            new BigDecimal("72050.0000"),
            null,
            null,
            null,
            null
        ),
        9101L
    );
    Instant quoteAsOf = Instant.parse("2026-03-01T10:01:59Z");
    BigDecimal preTradePrice = new BigDecimal("72050.0000");
    Order[] savedOrderRef = new Order[1];

    when(orderRepository.findByClOrdId(MARKET_PAYLOAD_BOUND_CL_ORD_ID))
        .thenAnswer(invocation -> Optional.ofNullable(savedOrderRef[0]));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(accountRepository.findById(2001L)).thenReturn(Optional.of(makerAccount));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(2001L, "005930")).thenReturn(Optional.of(makerPosition));
    when(orderRepository.lockExecutionRestingLimitOrdersForSweep(eq("005930"), eq("SELL"), any()))
        .thenReturn(List.of(makerOrder));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> {
          Order persisted = persistedOrder(invocation.getArgument(0), 9015L);
          savedOrderRef[0] = persisted;
          return persisted;
        });
    when(journalEntryRepository.save(any(JournalEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7015L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8015L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    fepClient.setSubmitResult(new FepOrderResult(
        MARKET_PAYLOAD_BOUND_CL_ORD_ID,
        "FEP-KRX-" + MARKET_PAYLOAD_BOUND_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        3L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null,
        null,
        null
    ));

    corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        MARKET_PAYLOAD_BOUND_CL_ORD_ID,
        "005930",
        "BUY",
        "MARKET",
        new BigDecimal("3.0000"),
        null,
        "qsnap-market-1",
        quoteAsOf,
        FepQuoteSourceMode.LIVE,
        preTradePrice
    ));

    assertThat(savedOrderRef[0]).isNotNull();
    assertThat(savedOrderRef[0].getOrderType()).isEqualTo("MARKET");
    assertThat(savedOrderRef[0].getOrderPrice()).isNull();
    assertThat(savedOrderRef[0].getQuoteSnapshotId()).isEqualTo("qsnap-market-1");
    assertThat(savedOrderRef[0].getQuoteAsOf()).isEqualTo(quoteAsOf);
    assertThat(savedOrderRef[0].getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
    assertThat(savedOrderRef[0].getPreTradePrice()).isEqualByComparingTo(preTradePrice);
    assertThat(fepClient.lastSubmitPayload()).isNotNull();
    assertThat(fepClient.lastSubmitPayload().orderType()).isEqualTo(com.fix.common.fep.FepOrderType.MARKET);
    assertThat(fepClient.lastSubmitPayload().price()).isNull();
    assertThat(fepClient.lastSubmitPayload().quoteSnapshotId()).isEqualTo("qsnap-market-1");
    assertThat(fepClient.lastSubmitPayload().quoteAsOf()).isEqualTo(quoteAsOf);
    assertThat(fepClient.lastSubmitPayload().quoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
    assertThat(fepClient.lastSubmitPayload().preTradePrice()).isEqualTo(72050L);
    assertThat(position.getQty()).isEqualByComparingTo("3.0000");
    assertThat(position.getAvgPrice()).isEqualByComparingTo("72050.0000");
    assertThat(makerOrder.getStatus()).isEqualTo("FILLED");
    assertThat(makerOrder.getExecutedQty()).isEqualByComparingTo("3.0000");
    assertThat(makerPosition.getQty()).isEqualByComparingTo("0.0000");

    ArgumentCaptor<Execution> executionCaptor = ArgumentCaptor.forClass(Execution.class);
    verify(executionRepository, times(2)).saveAndFlush(executionCaptor.capture());
    List<Execution> persistedExecutions = executionCaptor.getAllValues();
    Execution takerExecution = persistedExecutions.stream()
        .filter(execution -> savedOrderRef[0].getId().equals(execution.getOrderId()))
        .findFirst()
        .orElseThrow();
    Execution makerExecution = persistedExecutions.stream()
        .filter(execution -> makerOrder.getId().equals(execution.getOrderId()))
        .findFirst()
        .orElseThrow();
    assertThat(takerExecution.getQuoteSnapshotId()).isEqualTo("qsnap-market-1");
    assertThat(takerExecution.getQuoteAsOf()).isEqualTo(quoteAsOf);
    assertThat(takerExecution.getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
    assertThat(makerExecution.getQuoteSnapshotId()).isNull();
    assertThat(makerExecution.getQuoteAsOf()).isNull();
    assertThat(makerExecution.getQuoteSourceMode()).isNull();
  }

  @Test
  void shouldRejectMarketOrderWhenOppositeBookIsEmpty() {
    Account account = persistedAccount();

    when(orderRepository.findByClOrdId(MARKET_NO_LIQUIDITY_CL_ORD_ID)).thenReturn(Optional.empty());
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(orderRepository.lockExecutionRestingLimitOrdersForSweep(eq("005930"), eq("SELL"), any()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        MARKET_NO_LIQUIDITY_CL_ORD_ID,
        "005930",
        "BUY",
        "MARKET",
        new BigDecimal("3.0000"),
        null,
        "qsnap-market-empty",
        Instant.parse("2026-03-01T10:01:59Z"),
        FepQuoteSourceMode.LIVE,
        new BigDecimal("72050.0000")
    )))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORD_NO_LIQUIDITY);
          assertThat(ex.getDetails()).containsEntry("symbol", "005930");
          assertThat(ex.getDetails()).containsEntry("side", "BUY");
          assertThat(ex.getDetails()).containsEntry("orderQty", new BigDecimal("3.0000"));
        });

    assertThat(fepClient.submitCalls()).isZero();
    assertThat(account.getCashBalance()).isEqualByComparingTo("100000000.0000");
    verify(orderRepository, never()).saveAndFlush(any(Order.class));
    verify(executionRepository, never()).saveAndFlush(any(Execution.class));
    verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    verify(ledgerEntryRefRepository, never()).save(any(LedgerEntryRef.class));
    verify(accountRepository, never()).findByIdForUpdate(anyLong());
    verify(positionRepository, never()).findByAccountIdAndSymbolForUpdate(anyLong(), any(String.class));
  }

  @Test
  void shouldSweepMarketBuyAcrossOppositeBookInPriceTimeOrder() {
    Account takerAccount = persistedAccount();
    Position takerPosition = Position.of(ACCOUNT_ID, "005930", BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4));
    Account makerOneAccount = withId(
        Account.of("ACC-2001", 2001L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        2001L
    );
    Account makerTwoAccount = withId(
        Account.of("ACC-2002", 2002L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        2002L
    );
    Position makerOnePosition = Position.of(2001L, "005930", new BigDecimal("2.0000"), new BigDecimal("68000.0000"));
    Position makerTwoPosition = Position.of(2002L, "005930", new BigDecimal("3.0000"), new BigDecimal("69000.0000"));
    Order makerOneOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            2001L,
            "maker-sell-2001",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("2.0000"),
            new BigDecimal("70000.0000"),
            null,
            null,
            null,
            null
        ),
        9201L
    ), Instant.parse("2026-03-01T09:59:00Z"));
    Order makerTwoOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            2002L,
            "maker-sell-2002",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("3.0000"),
            new BigDecimal("70100.0000"),
            null,
            null,
            null,
            null
        ),
        9202L
    ), Instant.parse("2026-03-01T10:00:00Z"));
    Order[] savedOrderRef = new Order[1];

    when(orderRepository.findByClOrdId(MARKET_SWEEP_CL_ORD_ID))
        .thenAnswer(invocation -> Optional.ofNullable(savedOrderRef[0]));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(takerAccount));
    when(accountRepository.findById(2001L)).thenReturn(Optional.of(makerOneAccount));
    when(accountRepository.findById(2002L)).thenReturn(Optional.of(makerTwoAccount));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(takerPosition));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(2001L, "005930")).thenReturn(Optional.of(makerOnePosition));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(2002L, "005930")).thenReturn(Optional.of(makerTwoPosition));
    when(orderRepository.lockExecutionRestingLimitOrdersForSweep(eq("005930"), eq("SELL"), any()))
        .thenReturn(List.of(makerOneOrder, makerTwoOrder));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> {
          Order persisted = persistedOrder(invocation.getArgument(0), 9017L);
          savedOrderRef[0] = persisted;
          return persisted;
        });
    when(journalEntryRepository.save(any(JournalEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7017L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8017L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    fepClient.setSubmitResult(new FepOrderResult(
        MARKET_SWEEP_CL_ORD_ID,
        "FEP-KRX-" + MARKET_SWEEP_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        4L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null,
        null,
        null
    ));

    InternalOrderResult result = corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        MARKET_SWEEP_CL_ORD_ID,
        "005930",
        "BUY",
        "MARKET",
        new BigDecimal("4.0000"),
        null,
        "qsnap-market-sweep-1",
        Instant.parse("2026-03-01T10:01:59Z"),
        FepQuoteSourceMode.LIVE,
        new BigDecimal("72050.0000")
    ));

    assertThat(result.getStatus()).isEqualTo("FILLED");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("4.0000");
    assertThat(result.getLeavesQty()).isEqualByComparingTo("0.0000");
    assertThat(result.getExecutedPrice()).isEqualByComparingTo("70050.0000");
    assertThat(takerPosition.getQty()).isEqualByComparingTo("4.0000");
    assertThat(takerPosition.getAvgPrice()).isEqualByComparingTo("70050.0000");
    assertThat(takerAccount.getCashBalance()).isEqualByComparingTo("99719800.0000");
    assertThat(makerOnePosition.getQty()).isEqualByComparingTo("0.0000");
    assertThat(makerTwoPosition.getQty()).isEqualByComparingTo("1.0000");
    assertThat(makerTwoPosition.getAvgPrice()).isEqualByComparingTo("69000.0000");
    assertThat(makerOneOrder.getStatus()).isEqualTo("FILLED");
    assertThat(makerOneOrder.getExecutionResult()).isEqualTo("FILLED");
    assertThat(makerOneOrder.getExecutedQty()).isEqualByComparingTo("2.0000");
    assertThat(makerOneOrder.getLeavesQty()).isEqualByComparingTo("0.0000");
    assertThat(makerOneOrder.getExecutedPrice()).isEqualByComparingTo("70000.0000");
    assertThat(makerTwoOrder.getStatus()).isEqualTo("PARTIALLY_FILLED");
    assertThat(makerTwoOrder.getExecutionResult()).isEqualTo("PARTIALLY_FILLED");
    assertThat(makerTwoOrder.getExecutedQty()).isEqualByComparingTo("2.0000");
    assertThat(makerTwoOrder.getLeavesQty()).isEqualByComparingTo("1.0000");
    assertThat(makerTwoOrder.getExecutedPrice()).isEqualByComparingTo("70100.0000");
    assertThat(fepClient.lastSubmitPayload()).isNotNull();
    assertThat(fepClient.lastSubmitPayload().orderType()).isEqualTo(com.fix.common.fep.FepOrderType.MARKET);
  }

  @Test
  void shouldMarkMarketOrderPartiallyFilledFromPersistedFills() {
    Account takerAccount = persistedAccount();
    Position takerPosition = Position.of(ACCOUNT_ID, "005930", BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4));
    Account makerOneAccount = withId(
        Account.of("ACC-2001", 2001L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        2001L
    );
    Account makerTwoAccount = withId(
        Account.of("ACC-2002", 2002L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        2002L
    );
    Position makerOnePosition = Position.of(2001L, "005930", new BigDecimal("2.0000"), new BigDecimal("68000.0000"));
    Position makerTwoPosition = Position.of(2002L, "005930", new BigDecimal("1.5000"), new BigDecimal("69000.0000"));
    Order makerOneOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            2001L,
            "maker-sell-partial-1",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("2.0000"),
            new BigDecimal("70000.0000"),
            null,
            null,
            null,
            null
        ),
        9501L
    ), Instant.parse("2026-03-01T09:59:00Z"));
    Order makerTwoOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            2002L,
            "maker-sell-partial-2",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("1.5000"),
            new BigDecimal("70100.0000"),
            null,
            null,
            null,
            null
        ),
        9502L
    ), Instant.parse("2026-03-01T10:00:00Z"));
    Order[] savedOrderRef = new Order[1];

    when(orderRepository.findByClOrdId(MARKET_PARTIAL_CL_ORD_ID))
        .thenAnswer(invocation -> Optional.ofNullable(savedOrderRef[0]));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(takerAccount));
    when(accountRepository.findById(2001L)).thenReturn(Optional.of(makerOneAccount));
    when(accountRepository.findById(2002L)).thenReturn(Optional.of(makerTwoAccount));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(takerPosition));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(2001L, "005930")).thenReturn(Optional.of(makerOnePosition));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(2002L, "005930")).thenReturn(Optional.of(makerTwoPosition));
    when(orderRepository.lockExecutionRestingLimitOrdersForSweep(eq("005930"), eq("SELL"), any()))
        .thenReturn(List.of(makerOneOrder, makerTwoOrder));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> {
          Order persisted = persistedOrder(invocation.getArgument(0), 9510L);
          savedOrderRef[0] = persisted;
          return persisted;
        });
    when(journalEntryRepository.save(any(JournalEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7510L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8510L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    fepClient.setSubmitResult(new FepOrderResult(
        MARKET_PARTIAL_CL_ORD_ID,
        "FEP-KRX-" + MARKET_PARTIAL_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        5L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null,
        null,
        null
    ));

    InternalOrderResult result = corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        MARKET_PARTIAL_CL_ORD_ID,
        "005930",
        "BUY",
        "MARKET",
        new BigDecimal("5.0000"),
        null,
        "qsnap-market-partial-1",
        Instant.parse("2026-03-01T10:01:59Z"),
        FepQuoteSourceMode.LIVE,
        new BigDecimal("72050.0000")
    ));

    assertThat(result.getStatus()).isEqualTo("PARTIALLY_FILLED");
    assertThat(result.getExecutionResult()).isEqualTo("PARTIALLY_FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("3.5000");
    assertThat(result.getLeavesQty()).isEqualByComparingTo("1.5000");
    assertThat(result.getExecutedPrice()).isEqualByComparingTo("70042.8571");
    assertThat(savedOrderRef[0]).isNotNull();
    assertThat(savedOrderRef[0].getStatus()).isEqualTo("PARTIALLY_FILLED");
    assertThat(savedOrderRef[0].getExecutionResult()).isEqualTo("PARTIALLY_FILLED");
    assertThat(savedOrderRef[0].getExecutedQty()).isEqualByComparingTo("3.5000");
    assertThat(savedOrderRef[0].getLeavesQty()).isEqualByComparingTo("1.5000");
    assertThat(savedOrderRef[0].getExecutedPrice()).isEqualByComparingTo("70042.8571");
  }

  @Test
  void shouldAppendExecutionSequenceAfterExistingMakerFillHistory() {
    Account takerAccount = persistedAccount();
    Position takerPosition = Position.of(ACCOUNT_ID, "005930", BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4));
    Account makerAccount = withId(
        Account.of("ACC-2003", 2003L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        2003L
    );
    Position makerPosition = Position.of(2003L, "005930", new BigDecimal("4.0000"), new BigDecimal("69900.0000"));
    Order makerOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            2003L,
            "maker-sell-existing",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("4.0000"),
            new BigDecimal("70000.0000"),
            null,
            null,
            null,
            null
        ),
        9401L
    ), Instant.parse("2026-03-01T10:00:00Z"));
    Order[] savedOrderRef = new Order[1];

    when(orderRepository.findByClOrdId(LIMIT_CROSS_CL_ORD_ID))
        .thenAnswer(invocation -> Optional.ofNullable(savedOrderRef[0]));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(takerAccount));
    when(accountRepository.findById(2003L)).thenReturn(Optional.of(makerAccount));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(takerPosition));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(2003L, "005930")).thenReturn(Optional.of(makerPosition));
    when(orderRepository.lockExecutionRestingLimitOrdersForSweep(eq("005930"), eq("SELL"), any()))
        .thenReturn(List.of(makerOrder));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> {
          Order persisted = persistedOrder(invocation.getArgument(0), 9410L);
          savedOrderRef[0] = persisted;
          return persisted;
        });
    when(executionRepository.findLatestExecutionSequenceForUpdate(anyLong())).thenReturn(0);
    when(executionRepository.findLatestExecutionSequenceForUpdate(9401L)).thenReturn(2);
    when(journalEntryRepository.save(any(JournalEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7410L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8410L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    fepClient.setSubmitResult(new FepOrderResult(
        LIMIT_CROSS_CL_ORD_ID,
        "FEP-KRX-" + LIMIT_CROSS_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        2L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null,
        null,
        null
    ));

    corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        LIMIT_CROSS_CL_ORD_ID,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70000.0000")
    ));

    ArgumentCaptor<Execution> executionCaptor = ArgumentCaptor.forClass(Execution.class);
    verify(executionRepository, times(2)).saveAndFlush(executionCaptor.capture());
    List<Execution> persistedExecutions = executionCaptor.getAllValues();
    assertThat(executionSeqsForOrder(persistedExecutions, savedOrderRef[0].getId())).containsExactly(1);
    assertThat(executionSeqsForOrder(persistedExecutions, makerOrder.getId())).containsExactly(3);
  }

  @Test
  void shouldLockMarketParticipantsInDeterministicAccountAndPositionOrder() {
    Account takerAccount = persistedAccount();
    Position takerPosition = Position.of(ACCOUNT_ID, "005930", BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4));
    Account makerHighAccount = withId(
        Account.of("ACC-3002", 3002L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        3002L
    );
    Account makerLowAccount = withId(
        Account.of("ACC-1000", 1000L, "ACTIVE", "KRW", new BigDecimal("1000000.0000"), new BigDecimal("500.0000")),
        1000L
    );
    Position makerHighPosition = Position.of(3002L, "005930", new BigDecimal("2.0000"), new BigDecimal("68000.0000"));
    Position makerLowPosition = Position.of(1000L, "005930", new BigDecimal("2.0000"), new BigDecimal("69000.0000"));
    Order makerHighOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            3002L,
            "maker-sell-3002",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("2.0000"),
            new BigDecimal("70000.0000"),
            null,
            null,
            null,
            null
        ),
        9302L
    ), Instant.parse("2026-03-01T09:59:00Z"));
    Order makerLowOrder = withCreatedAt(persistedOrder(
        Order.accepted(
            1000L,
            "maker-sell-1000",
            "005930",
            "SELL",
            "LIMIT",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000"),
            null,
            null,
            null,
            null
        ),
        9100L
    ), Instant.parse("2026-03-01T10:00:00Z"));
    Order[] savedOrderRef = new Order[1];
    Map<Long, Account> lockedAccounts = Map.of(
        ACCOUNT_ID, takerAccount,
        1000L, makerLowAccount,
        3002L, makerHighAccount
    );
    Map<Long, Position> lockedPositions = Map.of(
        ACCOUNT_ID, takerPosition,
        1000L, makerLowPosition,
        3002L, makerHighPosition
    );
    when(orderRepository.findByClOrdId(MARKET_SWEEP_CL_ORD_ID)).thenAnswer(invocation -> Optional.ofNullable(savedOrderRef[0]));
    when(accountRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(lockedAccounts.get(invocation.getArgument(0))));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(anyLong(), eq("005930")))
        .thenAnswer(invocation -> Optional.ofNullable(lockedPositions.get(invocation.getArgument(0))));
    when(orderRepository.lockExecutionRestingLimitOrdersForSweep(eq("005930"), eq("SELL"), any()))
        .thenReturn(List.of(makerHighOrder, makerLowOrder));
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> {
          Order persisted = persistedOrder(invocation.getArgument(0), 9310L);
          savedOrderRef[0] = persisted;
          return persisted;
        });
    when(journalEntryRepository.save(any(JournalEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 7310L));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 8310L));
    when(ledgerEntryRefRepository.save(any(LedgerEntryRef.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    fepClient.setSubmitResult(new FepOrderResult(
        MARKET_SWEEP_CL_ORD_ID,
        "FEP-KRX-" + MARKET_SWEEP_CL_ORD_ID,
        FepExecType.PENDING_NEW,
        FepOrdStatus.PENDING,
        0L,
        null,
        3L,
        Instant.parse("2026-03-01T10:05:30Z"),
        null,
        null,
        null,
        null,
        null
    ));

    corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        MARKET_SWEEP_CL_ORD_ID,
        "005930",
        "BUY",
        "MARKET",
        new BigDecimal("3.0000"),
        null,
        "qsnap-market-sweep-1",
        Instant.parse("2026-03-01T10:01:59Z"),
        FepQuoteSourceMode.LIVE,
        new BigDecimal("72050.0000")
    ));

    InOrder lockStepInOrder = org.mockito.Mockito.inOrder(orderRepository, accountRepository, positionRepository);
    lockStepInOrder.verify(orderRepository).lockExecutionRestingLimitOrdersForSweep(eq("005930"), eq("SELL"), any());
    lockStepInOrder.verify(accountRepository).findByIdForUpdate(1000L);
    lockStepInOrder.verify(accountRepository).findByIdForUpdate(ACCOUNT_ID);
    lockStepInOrder.verify(accountRepository).findByIdForUpdate(3002L);
    lockStepInOrder.verify(positionRepository).findByAccountIdAndSymbolForUpdate(1000L, "005930");
    lockStepInOrder.verify(positionRepository).findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930");
    lockStepInOrder.verify(positionRepository).findByAccountIdAndSymbolForUpdate(3002L, "005930");

    InOrder accountLockInOrder = org.mockito.Mockito.inOrder(accountRepository);
    accountLockInOrder.verify(accountRepository).findByIdForUpdate(1000L);
    accountLockInOrder.verify(accountRepository).findByIdForUpdate(ACCOUNT_ID);
    accountLockInOrder.verify(accountRepository).findByIdForUpdate(3002L);

    InOrder positionLockInOrder = org.mockito.Mockito.inOrder(positionRepository);
    positionLockInOrder.verify(positionRepository).findByAccountIdAndSymbolForUpdate(1000L, "005930");
    positionLockInOrder.verify(positionRepository).findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930");
    positionLockInOrder.verify(positionRepository).findByAccountIdAndSymbolForUpdate(3002L, "005930");
  }

  @Test
  void shouldRejectMarketOrderWhenQuoteSnapshotIsStaleAtExecution() {
    when(orderRepository.findByClOrdId(MARKET_STALE_CL_ORD_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        MARKET_STALE_CL_ORD_ID,
        "005930",
        "BUY",
        "MARKET",
        new BigDecimal("3.0000"),
        null,
        "qsnap-market-stale-1",
        Instant.parse("2026-03-01T10:01:54Z"),
        FepQuoteSourceMode.LIVE,
        new BigDecimal("72050.0000")
    )))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STALE_QUOTE);
          assertThat(ex.getDetails()).containsEntry("symbol", "005930");
          assertThat(ex.getDetails()).containsEntry("snapshotAgeMs", 6_000L);
          assertThat(ex.getDetails()).containsEntry("quoteSnapshotId", "qsnap-market-stale-1");
          assertThat(ex.getDetails()).containsEntry("quoteSourceMode", "LIVE");
        });

    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldRejectMarketOrderWhenQuoteSnapshotIdIsMissing() {
    when(orderRepository.findByClOrdId(MARKET_MISSING_SNAPSHOT_CL_ORD_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        MARKET_MISSING_SNAPSHOT_CL_ORD_ID,
        "005930",
        "BUY",
        "MARKET",
        new BigDecimal("3.0000"),
        null,
        null,
        Instant.parse("2026-03-01T10:01:59Z"),
        FepQuoteSourceMode.LIVE,
        new BigDecimal("72050.0000")
    )))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
          assertThat(ex.getMessage()).contains("quoteSnapshotId is required for MARKET orders");
        });

    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldRejectMarketOrderWhenQuoteSourceModeIsMissing() {
    when(orderRepository.findByClOrdId(MARKET_MISSING_SOURCE_MODE_CL_ORD_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        MARKET_MISSING_SOURCE_MODE_CL_ORD_ID,
        "005930",
        "BUY",
        "MARKET",
        new BigDecimal("3.0000"),
        null,
        "qsnap-market-missing-source",
        Instant.parse("2026-03-01T10:01:59Z"),
        null,
        new BigDecimal("72050.0000")
    )))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
          assertThat(ex.getMessage()).contains("quoteSourceMode is required for MARKET orders");
        });

    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldRejectIdempotentReplayWhenMarketQuoteContextDiffers() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            IDEMPOTENT_CL_ORD_ID,
            "005930",
            "BUY",
            "MARKET",
            new BigDecimal("3.0000"),
            null,
            new BigDecimal("72050.0000"),
            "qsnap-market-1",
            Instant.parse("2026-03-01T10:01:59Z"),
            FepQuoteSourceMode.LIVE
        ),
        9016L
    );

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "BUY",
        "MARKET",
        new BigDecimal("3.0000"),
        null,
        "qsnap-market-2",
        Instant.parse("2026-03-01T10:01:59Z"),
        FepQuoteSourceMode.LIVE,
        new BigDecimal("72050.0000")
    )))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORD_INVALID_REQUEST);
          assertThat(ex.getMessage()).contains("clOrdId replay payload mismatch");
        });
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
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
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
        null,
        null,
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
  void shouldTranslatePositionLockConflictToCore003() {
    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.empty());
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccount()));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930"))
        .thenThrow(new CannotAcquireLockException("Lock wait timeout exceeded"));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "SELL",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CORE_CONCURRENCY_CONFLICT);
          assertThat(businessException.getMetadata()).isEqualTo(
              new ErrorMetadata("error.core.concurrency_conflict", "CONCURRENCY_FAILURE")
          );
          assertThat(businessException.getDetails()).containsEntry("failureReason", "POSITION_LOCK");
          assertThat(businessException.getDetails()).containsEntry("symbol", "005930");
        });

    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldRejectUnsupportedOrderTypeBeforePersistence() {
    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "BUY",
        "STOP",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000"),
        null,
        null,
        null,
        null
    )))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORD_INVALID_REQUEST);
          assertThat(ex.getMessage()).contains("orderType must be LIMIT or MARKET");
        });

    verify(orderRepository, times(1)).findByClOrdId(IDEMPOTENT_CL_ORD_ID);
    assertThat(fepClient.submitCalls()).isZero();
  }

  @Test
  void shouldNotTranslateAccountRowLockFailureToCore003() {
    lenient().when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID)).thenReturn(Optional.empty());
    lenient().when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(persistedAccount()));
    lenient().when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930"))
        .thenReturn(Optional.of(Position.of(
            ACCOUNT_ID,
            "005930",
            new BigDecimal("10.0000"),
            new BigDecimal("70000.0000")
        )));
    lenient().when(executionRepository.sumSellQuantityByAccountAndSymbolBetween(eq(ACCOUNT_ID), eq("005930"), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    lenient().when(accountRepository.findByIdForUpdate(ACCOUNT_ID))
        .thenThrow(new CannotAcquireLockException("Account row lock timeout"));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        IDEMPOTENT_CL_ORD_ID,
        "005930",
        "SELL",
        new BigDecimal("3.0000"),
        new BigDecimal("70200.0000")
    )))
        .isInstanceOf(CannotAcquireLockException.class)
        .hasMessageContaining("Account row lock timeout");

    assertThat(fepClient.submitCalls()).isZero();
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
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
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

    assertThat(savedOrder.getStatus()).isEqualTo("NEW");
    assertThat(savedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(savedOrder.getFailureReason()).isEqualTo("TIMEOUT");
    assertThat(fepClient.submitCalls()).isEqualTo(1);
  }

  @Test
  void shouldEscalateRejectedSubmitAfterCanonicalPosting() {
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
        9014L
    );

    when(orderRepository.findByClOrdId(IDEMPOTENT_CL_ORD_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(savedOrder));
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    when(positionRepository.findByAccountIdAndSymbolForUpdate(ACCOUNT_ID, "005930")).thenReturn(Optional.of(position));
    when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(savedOrder);
    fepClient.setSubmitFailure(new BusinessException(
        ErrorCode.FEP_ORDER_REJECTED,
        ErrorCode.FEP_ORDER_REJECTED.defaultMessage(),
        new ErrorMetadata("error.fep.rejected", "ORDER_REJECTED")
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
        .isEqualTo(ErrorCode.FEP_ORDER_REJECTED);

    assertThat(savedOrder.getStatus()).isEqualTo("NEW");
    assertThat(savedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    assertThat(savedOrder.getFailureReason()).isEqualTo("ORDER_REJECTED");
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
    existingOrder.completeExecution(
        "PENDING",
        "FILLED",
        new BigDecimal("2.0000"),
        BigDecimal.ZERO.setScale(4),
        new BigDecimal("70100.0000"),
        Instant.parse("2026-03-01T10:00:00Z")
    );

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.setQueryResult(new FepOrderResult(
        REQUERY_CL_ORD_ID,
        "FEP-KRX-" + REQUERY_CL_ORD_ID,
        null,
        FepOrdStatus.UNKNOWN,
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-01T10:10:00Z"),
        "order not found in exchange",
        null,
        null,
        null
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID));

    assertThat(result.getStatus()).isEqualTo("PENDING");
    assertThat(result.getMessage()).isEqualTo("order not found in exchange");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("2.0000");
    assertThat(result.getExecutedPrice()).isEqualByComparingTo("70100.0000");
    assertThat(result.getExternalOrderId()).isEqualTo("FEP-KRX-" + REQUERY_CL_ORD_ID);
    assertThat(result.getRetriable()).isTrue();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(result.getAttemptCount()).isEqualTo(1);
    assertThat(result.getMaxRetryCount()).isEqualTo(5);
    assertThat(fepClient.queryCalls()).isEqualTo(1);
  }

  @Test
  void shouldRetryTransientStatusQueryFailureBeforeReturningRequeryResult() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9011L
    );
    existingOrder.updateStatus("PENDING");

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.scriptQueryOutcomes(
        new BusinessException(ErrorCode.FEP_GATEWAY_TIMEOUT, ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage()),
        new FepOrderResult(
            REQUERY_CL_ORD_ID,
            null,
            null,
            FepOrdStatus.PENDING,
            null,
            null,
            null,
            null,
            Instant.parse("2026-03-01T10:10:30Z"),
            "pending at exchange",
            null,
            null,
            null
        )
    );

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("PENDING");
    assertThat(result.getMessage()).isEqualTo("pending at exchange");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getRetriable()).isTrue();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(fepClient.queryCalls()).isEqualTo(2);
  }

  @Test
  void shouldPreserveFirstRetriableFailureWhenLaterRetryHitsUnavailable() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9013L
    );
    existingOrder.updateStatus("UNKNOWN");

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.scriptQueryOutcomes(
        new BusinessException(
            ErrorCode.FEP_GATEWAY_TIMEOUT,
            ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage(),
            new ErrorMetadata("error.fep.timeout", "TIMEOUT")
        ),
        new BusinessException(
            ErrorCode.FEP_GATEWAY_UNAVAILABLE,
            ErrorCode.FEP_GATEWAY_UNAVAILABLE.defaultMessage(),
            new ErrorMetadata("error.fep.unavailable", "CIRCUIT_OPEN")
        )
    );

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("UNKNOWN");
    assertThat(result.getMessage()).isEqualTo("Exchange connectivity timeout");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getRetriable()).isTrue();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(existingOrder.getFailureReason()).isEqualTo("TIMEOUT");
    assertThat(fepClient.queryCalls()).isEqualTo(2);
  }

  @Test
  void shouldThrowOriginalRetriableFailureWhenOrderDisappearsDuringRetry() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9016L
    );
    existingOrder.updateStatus("PENDING");

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID))
        .thenReturn(Optional.of(existingOrder))
        .thenReturn(Optional.empty());
    fepClient.setQueryFailure(new BusinessException(
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage()
    ));

    assertThatThrownBy(() -> corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 1)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.FEP_GATEWAY_TIMEOUT);

    assertThat(fepClient.queryCalls()).isEqualTo(1);
  }

  @Test
  void shouldStopRetryWhenOrderBecomesTerminalBetweenAttempts() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9014L
    );
    existingOrder.updateStatus("PENDING");

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.onQueryCall(attempt -> {
      if (attempt == 1) {
        existingOrder.updateStatus("FILLED");
      }
    });
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
    assertThat(existingOrder.getFailureReason()).isNull();
    assertThat(fepClient.queryCalls()).isEqualTo(1);
  }

  @Test
  void shouldPreserveLatestTerminalStateWhenConcurrentUpdateWinsAfterSuccessfulRefresh() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9017L
    );
    existingOrder.updateStatus("PENDING");
    Order latestOrder = withVersion(
        persistedOrder(
            Order.accepted(
                ACCOUNT_ID,
                REQUERY_CL_ORD_ID,
                "005930",
                "BUY",
                new BigDecimal("2.0000"),
                new BigDecimal("70100.0000")
            ),
            9017L
        ),
        1L
    );
    latestOrder.updateState("FILLED", Order.EXTERNAL_SYNC_CONFIRMED, "FEP-KRX-REQUERY", null);

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID))
        .thenReturn(Optional.of(existingOrder))
        .thenReturn(Optional.of(existingOrder))
        .thenReturn(Optional.of(latestOrder));
    when(orderRepository.updateStateIfVersionMatches(
        eq(REQUERY_CL_ORD_ID),
        eq(existingOrder.getVersion()),
        any(String.class),
        any(),
        any(),
        any(),
        any(Instant.class)
    )).thenReturn(0);
    fepClient.setQueryResult(new FepOrderResult(
        REQUERY_CL_ORD_ID,
        "FEP-KRX-REQUERY",
        null,
        FepOrdStatus.PENDING,
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-01T10:10:30Z"),
        "pending at exchange",
        null,
        null,
        null
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("FILLED");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_CONFIRMED);
    assertThat(result.getRetriable()).isFalse();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(fepClient.queryCalls()).isEqualTo(1);
  }

  @Test
  void shouldPreserveLatestTerminalStateWhenConcurrentUpdateWinsAfterFailureRefresh() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9018L
    );
    existingOrder.updateStatus("PENDING");
    Order latestOrder = withVersion(
        persistedOrder(
            Order.accepted(
                ACCOUNT_ID,
                REQUERY_CL_ORD_ID,
                "005930",
                "BUY",
                new BigDecimal("2.0000"),
                new BigDecimal("70100.0000")
            ),
            9018L
        ),
        1L
    );
    latestOrder.updateState("FILLED", Order.EXTERNAL_SYNC_CONFIRMED, "FEP-KRX-REQUERY", null);

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID))
        .thenReturn(Optional.of(existingOrder))
        .thenReturn(Optional.of(existingOrder))
        .thenReturn(Optional.of(latestOrder));
    fepClient.setQueryFailure(new BusinessException(
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage()
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("FILLED");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_CONFIRMED);
    assertThat(result.getMessage()).isEqualTo("Exchange connectivity timeout");
    assertThat(result.getRetriable()).isFalse();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(fepClient.queryCalls()).isEqualTo(2);
  }

  @Test
  void shouldStopStatusQueryRetryAtConfiguredMaxAttempts() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9012L
    );
    existingOrder.updateStatus("UNKNOWN");

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    ReflectionTestUtils.setField(corebankOrderService, "statusQueryMaxAttempts", 3);
    fepClient.scriptQueryOutcomes(
        new BusinessException(ErrorCode.FEP_GATEWAY_TIMEOUT, ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage()),
        new BusinessException(ErrorCode.FEP_GATEWAY_UNAVAILABLE, ErrorCode.FEP_GATEWAY_UNAVAILABLE.defaultMessage()),
        new BusinessException(ErrorCode.FEP_GATEWAY_TIMEOUT, ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage())
    );

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 3));

    assertThat(result.getStatus()).isEqualTo("UNKNOWN");
    assertThat(result.getMessage()).isEqualTo("Exchange connectivity timeout");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getRetriable()).isTrue();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(result.getAttemptCount()).isEqualTo(3);
    assertThat(result.getMaxRetryCount()).isEqualTo(5);
    assertThat(fepClient.queryCalls()).isEqualTo(3);
  }

  @Test
  void shouldNotRetryNonRetriableStatusQueryFailure() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9015L
    );
    existingOrder.updateStatus("PENDING");

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.setQueryFailure(new BusinessException(
        ErrorCode.CORE_CONCURRENCY_CONFLICT,
        ErrorCode.CORE_CONCURRENCY_CONFLICT.defaultMessage()
    ));

    assertThatThrownBy(() -> corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CORE_CONCURRENCY_CONFLICT);

    assertThat(fepClient.queryCalls()).isEqualTo(1);
  }

  @Test
  void shouldSurfaceUnavailableWhenRetryBackoffIsInterrupted() {
    ReflectionTestUtils.setField(corebankOrderService, "statusQueryBackoffMs", 1L);
    Thread.currentThread().interrupt();

    try {
      assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(corebankOrderService, "applyStatusQueryBackoff"))
          .isInstanceOf(BusinessException.class)
          .extracting(ex -> ((BusinessException) ex).getErrorCode())
          .isEqualTo(ErrorCode.FEP_GATEWAY_UNAVAILABLE);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void shouldSurfaceRejectReasonOnRejectedRequery() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9007L
    );
    existingOrder.completeExecution(
        "PENDING",
        "FILLED",
        new BigDecimal("2.0000"),
        BigDecimal.ZERO.setScale(4),
        new BigDecimal("70100.0000"),
        Instant.parse("2026-03-01T10:00:00Z")
    );

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.setQueryResult(new FepOrderResult(
        REQUERY_CL_ORD_ID,
        "FEP-KRX-" + REQUERY_CL_ORD_ID,
        FepExecType.REJECTED,
        FepOrdStatus.REJECTED,
        null,
        null,
        null,
        Instant.parse("2026-03-01T10:10:00Z"),
        Instant.parse("2026-03-01T10:11:00Z"),
        null,
        "INSUFFICIENT_FUNDS",
        null,
        null
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("PENDING");
    assertThat(result.getMessage()).isEqualTo("INSUFFICIENT_FUNDS");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("2.0000");
    assertThat(result.getExternalOrderId()).isEqualTo("FEP-KRX-" + REQUERY_CL_ORD_ID);
    assertThat(result.getRetriable()).isFalse();
    assertThat(result.getEscalationRequired()).isTrue();
    assertThat(existingOrder.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
  }

  @Test
  void shouldSurfaceParseErrorOnMalformedRequery() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9008L
    );
    existingOrder.completeExecution(
        "PENDING",
        "FILLED",
        new BigDecimal("2.0000"),
        BigDecimal.ZERO.setScale(4),
        new BigDecimal("70100.0000"),
        Instant.parse("2026-03-01T10:00:00Z")
    );

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.setQueryResult(new FepOrderResult(
        REQUERY_CL_ORD_ID,
        "FEP-KRX-" + REQUERY_CL_ORD_ID,
        null,
        FepOrdStatus.MALFORMED,
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-01T10:11:00Z"),
        "FIX ExecutionReport parse failed; manual review required",
        null,
        null,
        "PARSE_ERROR:Tag 39 missing or invalid"
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("PENDING");
    assertThat(result.getMessage()).isEqualTo("PARSE_ERROR:Tag 39 missing or invalid");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("2.0000");
    assertThat(result.getExternalOrderId()).isEqualTo("FEP-KRX-" + REQUERY_CL_ORD_ID);
    assertThat(result.getRetriable()).isTrue();
    assertThat(result.getEscalationRequired()).isFalse();
    assertThat(existingOrder.getFailureReason()).isEqualTo("PARSE_ERROR:Tag 39 missing or invalid");
  }

  @Test
  void shouldEscalateCanceledRequeryWhilePreservingCanonicalExecutionState() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9009L
    );
    existingOrder.completeExecution(
        "PENDING",
        "FILLED",
        new BigDecimal("2.0000"),
        BigDecimal.ZERO.setScale(4),
        new BigDecimal("70100.0000"),
        Instant.parse("2026-03-01T10:00:00Z")
    );

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.setQueryResult(new FepOrderResult(
        REQUERY_CL_ORD_ID,
        "FEP-KRX-" + REQUERY_CL_ORD_ID,
        FepExecType.CANCELED,
        FepOrdStatus.CANCELED,
        2L,
        70100L,
        0L,
        Instant.parse("2026-03-01T10:10:00Z"),
        Instant.parse("2026-03-01T10:11:00Z"),
        "exchange canceled after canonical posting",
        null,
        2L,
        null
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("PENDING");
    assertThat(result.getMessage()).isEqualTo("exchange canceled after canonical posting");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("2.0000");
    assertThat(result.getLeavesQty()).isEqualByComparingTo("0.0000");
    assertThat(result.getExecutedPrice()).isEqualByComparingTo("70100.0000");
    assertThat(result.getExternalOrderId()).isEqualTo("FEP-KRX-" + REQUERY_CL_ORD_ID);
    assertThat(result.getRetriable()).isFalse();
    assertThat(result.getEscalationRequired()).isTrue();
  }

  @Test
  void shouldNotConfirmFilledRequeryWhenTerminalCanonicalStateIsDifferent() {
    Order existingOrder = persistedOrder(
        Order.accepted(
            ACCOUNT_ID,
            REQUERY_CL_ORD_ID,
            "005930",
            "BUY",
            new BigDecimal("2.0000"),
            new BigDecimal("70100.0000")
        ),
        9010L
    );
    existingOrder.updateState(
        "REJECTED",
        Order.EXTERNAL_SYNC_ESCALATED,
        "FEP-KRX-" + REQUERY_CL_ORD_ID,
        "ORDER_REJECTED"
    );

    when(orderRepository.findByClOrdId(REQUERY_CL_ORD_ID)).thenReturn(Optional.of(existingOrder));
    fepClient.setQueryResult(new FepOrderResult(
        REQUERY_CL_ORD_ID,
        "FEP-KRX-" + REQUERY_CL_ORD_ID,
        FepExecType.FILL,
        FepOrdStatus.FILLED,
        2L,
        70100L,
        0L,
        Instant.parse("2026-03-01T10:10:00Z"),
        Instant.parse("2026-03-01T10:11:00Z"),
        null,
        null,
        null,
        null
    ));

    InternalOrderResult result = corebankOrderService.requeryOrder(InternalOrderRequeryCommand.of(REQUERY_CL_ORD_ID, 2));

    assertThat(result.getStatus()).isEqualTo("REJECTED");
    assertThat(result.getMessage()).isEqualTo("ORDER_REJECTED");
    assertThat(result.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    assertThat(result.getRetriable()).isFalse();
    assertThat(result.getEscalationRequired()).isTrue();
    assertThat(existingOrder.getFailureReason()).isEqualTo("ORDER_REJECTED");
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
        "still unresolved",
        null,
        null,
        null
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
    assertThat(fepClient.queryCalls()).isEqualTo(2);
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
    assertThat(fepClient.queryCalls()).isEqualTo(1);
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
    Method transitionAccountStatus = CorebankOrderPersistenceService.class.getMethod(
        "transitionAccountStatus",
        AccountStatusTransitionCommand.class
    );

    assertThat(createOrder.getAnnotation(Transactional.class)).isNull();
    assertThat(requeryOrder.getAnnotation(Transactional.class)).isNull();
    assertThat(prepareOrderSubmission.getAnnotation(Transactional.class)).isNotNull();
    assertThat(getRequiredOrder.getAnnotation(Transactional.class)).isNotNull();
    assertThat(getRequiredOrder.getAnnotation(Transactional.class).readOnly()).isTrue();
    assertThat(updateOrderState.getAnnotation(Transactional.class)).isNotNull();
    assertThat(transitionAccountStatus.getAnnotation(Transactional.class)).isNotNull();
  }

  private Account persistedAccount() {
    Account account = persistedAccountWithStatus("ACTIVE");
    return withId(account, ACCOUNT_ID);
  }

  private Account persistedAccountWithStatus(String status) {
    return Account.of(
        "ACC-1001",
        OWNER_MEMBER_ID,
        status,
        "KRW",
        new BigDecimal("100000000.0000"),
        new BigDecimal("500.0000")
    );
  }

  private Order persistedOrder(Order order, Long id) {
    return withVersion(withId(order, id), 0L);
  }

  private <T> T withId(T target, Long id) {
    ReflectionTestUtils.setField(target, "id", id);
    return target;
  }

  private <T> T withUpdatedAt(T target, Instant updatedAt) {
    ReflectionTestUtils.setField(target, "updatedAt", updatedAt);
    return target;
  }

  private <T> T withCreatedAt(T target, Instant createdAt) {
    ReflectionTestUtils.setField(target, "createdAt", createdAt);
    return target;
  }

  private <T> T withVersion(T target, Long version) {
    ReflectionTestUtils.setField(target, "version", version);
    return target;
  }

  private List<Integer> executionSeqsForOrder(List<Execution> executions, Long orderId) {
    return executions.stream()
        .filter(execution -> orderId.equals(execution.getOrderId()))
        .map(Execution::getExecutionSeq)
        .toList();
  }

  private FepQuoteSnapshotResult quoteSnapshot(
      String quoteSnapshotId,
      String symbol,
      Instant quoteAsOf,
      Long bestBid,
      Long bestAsk,
      Long lastTrade
  ) {
    return new FepQuoteSnapshotResult(
        quoteSnapshotId,
        symbol,
        FepQuoteSourceMode.LIVE,
        quoteAsOf,
        bestBid,
        bestAsk,
        lastTrade,
        42L,
        false
    );
  }

  private static final class StubFepClient extends FepClient {

    private FepOrderResult submitResult;
    private FepOrderResult queryResult;
    private RuntimeException submitFailure;
    private RuntimeException queryFailure;
    private final Deque<Object> scriptedQueryOutcomes = new ArrayDeque<>();
    private FepOutboundOrderPayload lastSubmitPayload;
    private Runnable onSubmit = () -> {
    };
    private IntConsumer onQueryCall = attempt -> {
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
      onQueryCall.accept(queryCalls);
      if (!scriptedQueryOutcomes.isEmpty()) {
        Object outcome = scriptedQueryOutcomes.removeFirst();
        if (outcome instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }
        return (FepOrderResult) outcome;
      }
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
      scriptedQueryOutcomes.clear();
      this.queryResult = queryResult;
    }

    private void setQueryFailure(RuntimeException queryFailure) {
      scriptedQueryOutcomes.clear();
      this.queryFailure = queryFailure;
    }

    private void scriptQueryOutcomes(Object... outcomes) {
      scriptedQueryOutcomes.clear();
      for (Object outcome : outcomes) {
        scriptedQueryOutcomes.addLast(outcome);
      }
      queryResult = null;
      queryFailure = null;
    }

    private void onSubmit(Runnable onSubmit) {
      this.onSubmit = onSubmit;
    }

    private void onQueryCall(IntConsumer onQueryCall) {
      this.onQueryCall = onQueryCall;
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

  private static final class StubFepQuoteSnapshotClient extends FepQuoteSnapshotClient {

    private final Map<String, FepQuoteSnapshotResult> quoteResults = new HashMap<>();
    private final Map<String, RuntimeException> quoteFailures = new HashMap<>();
    private int singleQueryCalls;
    private int batchQueryCalls;

    private StubFepQuoteSnapshotClient() {
      super(RestClient.builder().baseUrl("http://localhost").build(), "test-secret");
    }

    @Override
    public FepQuoteSnapshotResult queryLatestQuoteSnapshot(
        String symbol,
        FepQuoteSourceMode quoteSourceMode,
        String correlationId
    ) {
      singleQueryCalls++;
      RuntimeException failure = quoteFailures.get(symbol);
      if (failure != null) {
        throw failure;
      }
      FepQuoteSnapshotResult result = quoteResults.get(symbol);
      if (result == null) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "quote snapshot not found");
      }
      return result;
    }

    @Override
    public Map<String, FepQuoteSnapshotResult> queryLatestQuoteSnapshots(
        List<String> symbols,
        FepQuoteSourceMode quoteSourceMode,
        String correlationId
    ) {
      batchQueryCalls++;
      Map<String, FepQuoteSnapshotResult> snapshots = new HashMap<>();
      for (String symbol : symbols) {
        RuntimeException failure = quoteFailures.get(symbol);
        if (failure != null) {
          throw failure;
        }
        FepQuoteSnapshotResult result = quoteResults.get(symbol);
        if (result != null) {
          snapshots.put(symbol, result);
        }
      }
      return snapshots;
    }

    private void setQuoteResult(String symbol, FepQuoteSnapshotResult result) {
      quoteFailures.remove(symbol);
      quoteResults.put(symbol, result);
    }

    private void setQuoteFailure(String symbol, RuntimeException failure) {
      quoteResults.remove(symbol);
      quoteFailures.put(symbol, failure);
    }

    private int singleQueryCalls() {
      return singleQueryCalls;
    }

    private int batchQueryCalls() {
      return batchQueryCalls;
    }
  }
}
