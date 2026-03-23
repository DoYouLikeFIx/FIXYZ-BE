package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fix.channel.entity.Member;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.ManualRecoveryQueueEntryRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.vo.AccountPositionQueryCommand;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.channel.vo.AccountSummaryQueryCommand;
import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.channel.vo.OrderSessionOtpVerifyCommand;
import com.fix.channel.vo.OrderSessionQueryCommand;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_service_orders;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.session.store-type=none",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
class OrderSessionServiceTest {

  @Autowired
  private OrderSessionService orderSessionService;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private OrderSessionRepository orderSessionRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Autowired
  private SecurityEventRepository securityEventRepository;

  @Autowired
  private ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository;

  @Autowired
  private InMemoryOrderSessionTtlStore orderSessionTtlStore;

  @Autowired
  private FaultInjectingOrderSessionPersistenceService orderSessionPersistenceService;

  @Autowired
  private RecordingOrderSessionRateLimitService orderSessionRateLimitService;

  @Autowired
  private StubAccountPositionService accountPositionService;

  @Autowired
  private TotpService totpService;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private Member ownerMember;
  private Member otherMember;
  private TransactionTemplate requiresNewTransactionTemplate;

  @BeforeEach
  void setUp() {
    securityEventRepository.deleteAll();
    auditLogRepository.deleteAll();
    manualRecoveryQueueEntryRepository.deleteAll();
    orderSessionRepository.deleteAll();
    memberRepository.deleteAll();
    orderSessionTtlStore.reset();
    orderSessionPersistenceService.reset();
    orderSessionRateLimitService.reset();
    ownerMember = saveLinkedMember("M-ORD-SVC-001", "svc-owner@fixyz.com", "Service Owner", 101L, "12345678901234");
    otherMember = saveLinkedMember("M-ORD-SVC-002", "svc-other@fixyz.com", "Service Other", 202L, "43210987654321");
    accountPositionService.reset(ownerMember.getAccountId(), ownerMember.getId());
    requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
    requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Test
  void shouldCreatePendingNewSessionWithTtlMetadata() {
    var result = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174260",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));

    assertThat(result.isCreated()).isTrue();
    assertThat(result.getOrderSessionId()).isNotBlank();
    assertThat(result.getStatus()).isEqualTo("PENDING_NEW");
    assertThat(result.isChallengeRequired()).isTrue();
    assertThat(result.getAuthorizationReason()).isEqualTo("ELEVATED_ORDER_RISK");
    assertThat(result.getAccountId()).isEqualTo(101L);
    assertThat(result.getSymbol()).isEqualTo("005930");
    assertThat(result.getSide()).isEqualTo("BUY");
    assertThat(result.getOrderType()).isEqualTo("LIMIT");
    assertThat(result.getQty()).isEqualByComparingTo("10");
    assertThat(result.getPrice()).isEqualByComparingTo("72000");
    assertThat(result.getQuoteSnapshotId()).isNull();
    assertThat(result.getQuoteAsOf()).isNull();
    assertThat(result.getQuoteSourceMode()).isNull();
    assertThat(result.getPreTradePrice()).isNull();
    assertThat(result.getRemainingSeconds()).isBetween(1L, 3600L);
    assertThat(result.getExpiresAt()).isNotNull();
    assertThat(result.getCreatedAt()).isNotNull();
    assertThat(result.getUpdatedAt()).isNotNull();
    assertThat(auditLogRepository.count()).isEqualTo(1L);
    assertThat(orderSessionRepository.findByOrderSessionId(result.getOrderSessionId()))
        .hasValueSatisfying(session -> {
          assertThat(session.getExpiresAt()).isEqualTo(result.getExpiresAt());
          assertThat(session.getAccountId()).isEqualTo(101L);
          assertThat(session.getSymbol()).isEqualTo("005930");
          assertThat(session.isChallengeRequired()).isTrue();
          assertThat(session.getAuthorizationReason()).isEqualTo("ELEVATED_ORDER_RISK");
          assertThat(session.getQuoteSnapshotId()).isNull();
          assertThat(session.getQuoteAsOf()).isNull();
          assertThat(session.getQuoteSourceMode()).isNull();
          assertThat(session.getPreTradePrice()).isNull();
        });
  }

  @Test
  void shouldPopulateQuoteContextWhenCreatingMarketBuySession() {
    var result = orderSessionService.createOrderSession(OrderSessionCreateCommand.of(
        ownerMember.getId(),
        ownerMember.getAccountId(),
        "123e4567-e89b-42d3-a456-426614174275",
        "005930",
        "BUY",
        "MARKET",
        BigDecimal.TEN,
        null
    ));

    assertThat(result.getOrderType()).isEqualTo("MARKET");
    assertThat(result.getPrice()).isNull();
    assertThat(result.getQuoteSnapshotId()).isEqualTo("qsnap_005930_live_001");
    assertThat(result.getQuoteAsOf()).isEqualTo(Instant.parse("2026-03-20T00:00:00Z"));
    assertThat(result.getQuoteSourceMode()).isEqualTo("LIVE");
    assertThat(result.getPreTradePrice()).isEqualByComparingTo("72050.0000");
    assertThat(orderSessionRepository.findByOrderSessionId(result.getOrderSessionId()))
        .hasValueSatisfying(session -> {
          assertThat(session.getQuoteSnapshotId()).isEqualTo("qsnap_005930_live_001");
          assertThat(session.getQuoteAsOf()).isEqualTo(Instant.parse("2026-03-20T00:00:00Z"));
          assertThat(session.getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
          assertThat(session.getPreTradePrice()).isEqualByComparingTo("72050.0000");
        });
  }

  @Test
  void shouldRecordAuditAndRejectWhenMarketQuoteIsStale() {
    accountPositionService.failNextWith(new BusinessException(
        ErrorCode.STALE_QUOTE,
        ErrorCode.STALE_QUOTE.defaultMessage(),
        null,
        Map.of(
            "symbol", "005930",
            "snapshotAgeMs", 6000,
            "quoteSourceMode", "LIVE",
            "quoteSnapshotId", "qsnap_005930_live_999"
        )
    ));

    assertThatThrownBy(() -> orderSessionService.createOrderSession(OrderSessionCreateCommand.of(
        ownerMember.getId(),
        ownerMember.getAccountId(),
        "123e4567-e89b-42d3-a456-426614174276",
        "005930",
        "BUY",
        "MARKET",
        BigDecimal.TEN,
        null
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.STALE_QUOTE));

    assertThat(orderSessionRepository.findByClOrdId("123e4567-e89b-42d3-a456-426614174276")).isEmpty();
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_FAILED");
          assertThat(log.getTargetId()).isEqualTo("123e4567-e89b-42d3-a456-426614174276");
          assertThat(log.getDetail()).contains("reason=STALE_QUOTE");
          assertThat(log.getDetail()).contains("symbol=005930");
          assertThat(log.getDetail()).contains("snapshotAgeMs=6000");
          assertThat(log.getDetail()).contains("quoteSourceMode=LIVE");
        });
  }

  @Test
  void shouldAutoAuthorizeLowRiskSessionWhenTrustedAuthSessionWindowIsFresh() {
    Instant freshMfaVerifiedAt = Instant.now().minusSeconds(30);

    var result = orderSessionService.createOrderSession(OrderSessionCreateCommand.of(
        ownerMember.getId(),
        ownerMember.getAccountId(),
        "123e4567-e89b-42d3-a456-426614174270",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(10000),
        freshMfaVerifiedAt,
        "127.0.0.1",
        "MockMvc",
        "127.0.0.1",
        "MockMvc"
    ));

    assertThat(result.isCreated()).isTrue();
    assertThat(result.getStatus()).isEqualTo("AUTHED");
    assertThat(result.isChallengeRequired()).isFalse();
    assertThat(result.getAuthorizationReason()).isEqualTo("TRUSTED_AUTH_SESSION");
    assertThat(orderSessionRepository.findByOrderSessionId(result.getOrderSessionId()))
        .hasValueSatisfying(session -> {
          assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.AUTHED);
          assertThat(session.isChallengeRequired()).isFalse();
          assertThat(session.getAuthorizationReason()).isEqualTo("TRUSTED_AUTH_SESSION");
        });
  }

  @Test
  void shouldExtendOwnedActiveSessionToFullTtlWindow() {
    var created = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174280",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));

    var extended = orderSessionService.extendOrderSession(
        OrderSessionQueryCommand.of(ownerMember.getId(), created.getOrderSessionId())
    );

    assertThat(extended.getOrderSessionId()).isEqualTo(created.getOrderSessionId());
    assertThat(extended.getRemainingSeconds()).isBetween(3590L, 3600L);
    assertThat(extended.getExpiresAt()).isAfterOrEqualTo(created.getExpiresAt());
    assertThat(orderSessionRepository.findByOrderSessionId(created.getOrderSessionId()))
        .hasValueSatisfying(session -> assertThat(session.getExpiresAt()).isAfterOrEqualTo(created.getExpiresAt()));
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> assertThat(log.getAction()).isEqualTo("ORDER_SESSION_EXTENDED"));
  }

  @Test
  void shouldExtendActiveSessionEvenWhenTrustedAuthWindowIsStale() {
    Instant staleMfaVerifiedAt = Instant.now().minus(Duration.ofMinutes(61));

    var created = orderSessionService.createOrderSession(OrderSessionCreateCommand.of(
        ownerMember.getId(),
        ownerMember.getAccountId(),
        "123e4567-e89b-42d3-a456-426614174281",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(10000),
        staleMfaVerifiedAt,
        "127.0.0.1",
        "MockMvc",
        "127.0.0.1",
        "MockMvc"
    ));

    assertThat(created.getStatus()).isEqualTo("PENDING_NEW");
    assertThat(created.isChallengeRequired()).isTrue();
    assertThat(created.getAuthorizationReason()).isEqualTo("ELEVATED_ORDER_RISK");

    var extended = orderSessionService.extendOrderSession(
        OrderSessionQueryCommand.of(ownerMember.getId(), created.getOrderSessionId())
    );

    assertThat(extended.getOrderSessionId()).isEqualTo(created.getOrderSessionId());
    assertThat(extended.getStatus()).isEqualTo("PENDING_NEW");
    assertThat(extended.getRemainingSeconds()).isBetween(3590L, 3600L);
    assertThat(extended.getExpiresAt()).isAfterOrEqualTo(created.getExpiresAt());
  }

  @Test
  void shouldRequireStepUpWhenLoginContextChangesEvenForLowRiskOrder() {
    Instant freshMfaVerifiedAt = Instant.now().minusSeconds(30);

    var result = orderSessionService.createOrderSession(OrderSessionCreateCommand.of(
        ownerMember.getId(),
        ownerMember.getAccountId(),
        "123e4567-e89b-42d3-a456-426614174271",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(10000),
        freshMfaVerifiedAt,
        "127.0.0.1",
        "MockMvc",
        "10.0.0.44",
        "DifferentDevice"
    ));

    assertThat(result.getStatus()).isEqualTo("PENDING_NEW");
    assertThat(result.isChallengeRequired()).isTrue();
    assertThat(result.getAuthorizationReason()).isEqualTo("ELEVATED_ORDER_RISK");
  }

  @Test
  void shouldRequireStepUpWhenRecentSecurityEventExists() {
    Instant freshMfaVerifiedAt = Instant.now().minusSeconds(30);
    memberRepository.flush();

    securityEventRepository.save(SecurityEvent.of(
        ownerMember.getId(),
        "MFA_REBIND_COMPLETED",
        "127.0.0.1",
        "MockMvc",
        "HIGH"
    ));

    var result = orderSessionService.createOrderSession(OrderSessionCreateCommand.of(
        ownerMember.getId(),
        ownerMember.getAccountId(),
        "123e4567-e89b-42d3-a456-426614174272",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(10000),
        freshMfaVerifiedAt,
        "127.0.0.1",
        "MockMvc",
        "127.0.0.1",
        "MockMvc"
    ));

    assertThat(result.getStatus()).isEqualTo("PENDING_NEW");
    assertThat(result.isChallengeRequired()).isTrue();
  }

  @Test
  void shouldRejectCreateWhenAvailableCashIsInsufficient() {
    accountPositionService.setAvailableBalance(BigDecimal.valueOf(50_000));

    assertThatThrownBy(() -> orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174273",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException actual = (BusinessException) ex;
          assertThat(actual.getErrorCode()).isEqualTo(ErrorCode.ORD_INSUFFICIENT_CASH);
          assertThat(actual.getMetadata()).isNotNull();
          assertThat(actual.getMetadata().userMessageKey()).isEqualTo("error.order.insufficient_cash");
          assertThat(actual.getMetadata().operatorCode()).isEqualTo("INSUFFICIENT_CASH");
        });
  }

  @Test
  void shouldRejectSellCreateWhenAvailableQuantityIsInsufficient() {
    accountPositionService.setAvailableQuantity(BigDecimal.valueOf(5));

    assertThatThrownBy(() -> orderSessionService.createOrderSession(OrderSessionCreateCommand.of(
        ownerMember.getId(),
        ownerMember.getAccountId(),
        "123e4567-e89b-42d3-a456-426614174274",
        "005930",
        "SELL",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException actual = (BusinessException) ex;
          assertThat(actual.getErrorCode()).isEqualTo(ErrorCode.ORD_INSUFFICIENT_POSITION);
          assertThat(actual.getMetadata()).isNotNull();
          assertThat(actual.getMetadata().userMessageKey()).isEqualTo("error.order.insufficient_position");
          assertThat(actual.getMetadata().operatorCode()).isEqualTo("INSUFFICIENT_POSITION");
        });
  }

  @Test
  void shouldRejectCreateWhenLinkedAccountDoesNotBelongToMember() {
    assertThatThrownBy(() -> orderSessionService.createOrderSession(OrderSessionCreateCommand.of(
        ownerMember.getId(),
        otherMember.getAccountId(),
        "123e4567-e89b-42d3-a456-426614174299",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(70000)
    )))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.CHANNEL_OWNERSHIP_MISMATCH));
  }

  @Test
  void shouldReturnReplayForExistingActiveSession() {
    var created = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174261",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));

    var replayed = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174261",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));

    assertThat(replayed.isCreated()).isFalse();
    assertThat(replayed.getOrderSessionId()).isEqualTo(created.getOrderSessionId());
    assertThat(replayed.getExpiresAt()).isEqualTo(created.getExpiresAt());
    assertThat(replayed.isChallengeRequired()).isTrue();
    assertThat(replayed.getAuthorizationReason()).isEqualTo("ELEVATED_ORDER_RISK");
    assertThat(auditLogRepository.count()).isEqualTo(1L);
  }

  @Test
  void shouldReturnAuthorizationDecisionMetadataOnOwnedStatusLookup() {
    var created = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174271",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));

    var loaded = orderSessionService.getOrderSession(
        OrderSessionQueryCommand.of(ownerMember.getId(), created.getOrderSessionId())
    );

    assertThat(loaded.getOrderSessionId()).isEqualTo(created.getOrderSessionId());
    assertThat(loaded.getStatus()).isEqualTo("PENDING_NEW");
    assertThat(loaded.isChallengeRequired()).isTrue();
    assertThat(loaded.getAuthorizationReason()).isEqualTo("ELEVATED_ORDER_RISK");
  }

  @Test
  void shouldRejectReplayWhenRequestPayloadDiffers() {
    orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174262",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));

    assertThatThrownBy(() -> orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174262",
        BigDecimal.TEN,
        BigDecimal.valueOf(72100)
    )))
        .isInstanceOf(BusinessException.class)
        .hasMessage("clOrdId replay payload mismatch");
  }

  @Test
  void shouldCleanupPersistedSessionWhenTtlActivationFails() {
    orderSessionTtlStore.failNextActivation();

    assertThatThrownBy(() -> orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174263",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    )))
        .isInstanceOf(BusinessException.class)
        .hasMessage("order session cache unavailable");

    assertThat(orderSessionRepository.findByClOrdId("123e4567-e89b-42d3-a456-426614174263")).isEmpty();
    assertThat(auditLogRepository.findAll())
        .extracting(log -> log.getAction())
        .containsExactlyInAnyOrder("ORDER_SESSION_CREATE", "ORDER_SESSION_FAILED");
  }

  @Test
  void shouldReturnNotFoundWhenFreshCreateLosesTtlBeforeResponseBuild() {
    orderSessionTtlStore.dropTtlOnNextActivation();

    assertThatThrownBy(() -> orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174266",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    )))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Order session not found.");

    assertThat(orderSessionRepository.findByClOrdId("123e4567-e89b-42d3-a456-426614174266"))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.EXPIRED));
  }

  @Test
  void shouldExpireSessionWhenLookupFindsNoLiveTtl() {
    var created = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174264",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));
    orderSessionTtlStore.clear(created.getOrderSessionId());

    assertThatThrownBy(() -> orderSessionService.getOrderSession(
        OrderSessionQueryCommand.of(ownerMember.getId(), created.getOrderSessionId())
    ))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Order session not found.");

    assertThat(orderSessionRepository.findByOrderSessionId(created.getOrderSessionId()))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.EXPIRED));
    assertThat(auditLogRepository.findAll())
        .anySatisfy(log -> {
          assertThat(log.getAction()).isEqualTo("ORDER_SESSION_EXPIRED");
          assertThat(log.getTargetId()).isEqualTo(created.getOrderSessionId());
        });
  }

  @Test
  void shouldExpireSessionWhenAuthoritativeExpiryHasPassed() {
    var created = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174268",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));
    orderSessionTtlStore.forceExpiry(created.getOrderSessionId(), Instant.now().minusSeconds(1));

    assertThatThrownBy(() -> orderSessionService.getOrderSession(
        OrderSessionQueryCommand.of(ownerMember.getId(), created.getOrderSessionId())
    ))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Order session not found.");

    assertThat(orderSessionRepository.findByOrderSessionId(created.getOrderSessionId()))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.EXPIRED));
  }

  @Test
  void shouldReturnCompletedSessionWithoutActiveWindowMetadataEvenAfterRedisTtlDisappears() {
    var created = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174288",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));
    OrderSession completed = orderSessionRepository.findByOrderSessionId(created.getOrderSessionId()).orElseThrow();
    completed.authorize();
    completed.startExecuting();
    completed.complete(
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "FEP-0001",
        "CONFIRMED",
        Instant.parse("2026-03-12T00:05:30Z")
    );
    orderSessionRepository.saveAndFlush(completed);
    orderSessionTtlStore.clear(created.getOrderSessionId());

    var loaded = orderSessionService.getOrderSession(
        OrderSessionQueryCommand.of(ownerMember.getId(), created.getOrderSessionId())
    );

    assertThat(loaded.getStatus()).isEqualTo("COMPLETED");
    assertThat(loaded.getExpiresAt()).isNull();
    assertThat(loaded.getRemainingSeconds()).isNull();
    assertThat(loaded.getExecutionResult()).isEqualTo("FILLED");
    assertThat(loaded.getExecutedQty()).isEqualByComparingTo("10");
    assertThat(loaded.getLeavesQty()).isEqualByComparingTo("0");
  }

  @Test
  void shouldCarryExecuteIdempotencyIntoOrderSessionResult() {
    var created = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174289",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));
    OrderSession completed = orderSessionRepository.findByOrderSessionId(created.getOrderSessionId()).orElseThrow();
    completed.authorize();
    completed.startExecuting();
    completed.complete(
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "FEP-0002",
        "CONFIRMED",
        Instant.parse("2026-03-12T00:05:30Z")
    );
    orderSessionRepository.saveAndFlush(completed);

    var result = orderSessionService.toResult(completed, false, true);

    assertThat(result.getStatus()).isEqualTo("COMPLETED");
    assertThat(result.getIdempotent()).isTrue();
    assertThat(result.getExternalOrderId()).isEqualTo("FEP-0002");
    assertThat(result.getExternalSyncStatus()).isEqualTo("CONFIRMED");
  }

  @Test
  void shouldTreatExpiredReplayAsNotFoundBeforePayloadValidation() {
    var created = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174265",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));
    orderSessionTtlStore.clear(created.getOrderSessionId());

    assertThatThrownBy(() -> orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174265",
        BigDecimal.TEN,
        BigDecimal.valueOf(72100)
    )))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Order session not found.");
  }

  @Test
  void shouldRecoverConcurrentDuplicateExceptionAndRefundRateLimit() {
    orderSessionPersistenceService.failNextCreateWithDuplicateAfterInsert();

    var replayed = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174267",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));

    assertThat(replayed.isCreated()).isFalse();
    assertThat(replayed.getOrderSessionId()).isNotBlank();
    assertThat(replayed.getStatus()).isEqualTo("PENDING_NEW");
    assertThat(replayed.isChallengeRequired()).isTrue();
    assertThat(replayed.getAuthorizationReason()).isEqualTo("ELEVATED_ORDER_RISK");
    assertThat(orderSessionRateLimitService.enforcementCount(ownerMember.getId())).isEqualTo(1);
    assertThat(orderSessionRateLimitService.refundCount(ownerMember.getId())).isEqualTo(1);
    assertThat(auditLogRepository.count()).isEqualTo(1L);
  }

  @Test
  void shouldRefundRateLimitWhenDuplicateRecoveryCannotLoadConcurrentSession() {
    orderSessionPersistenceService.failNextCreateWithDuplicateWithoutInsert();

    assertThatThrownBy(() -> orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174269",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    )))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessage("simulated duplicate create race without persisted session");

    assertThat(orderSessionRateLimitService.enforcementCount(ownerMember.getId())).isEqualTo(1);
    assertThat(orderSessionRateLimitService.refundCount(ownerMember.getId())).isEqualTo(1);
    assertThat(auditLogRepository.count()).isZero();
  }

  @Test
  void shouldReleaseReplayClaimWhenAuthorizationPersistenceFails() {
    ownerMember.enableTotpEnrollment();
    memberRepository.saveAndFlush(ownerMember);
    totpService.provisionActiveSecret(ownerMember);

    var firstSession = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174298",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));
    String otpCode = totpService.currentCode(ownerMember);

    orderSessionPersistenceService.failNextAuthorize();
    assertThatThrownBy(() -> orderSessionService.verifyOtp(
        OrderSessionOtpVerifyCommand.of(ownerMember.getId(), firstSession.getOrderSessionId(), otpCode)
    )).isInstanceOf(IllegalStateException.class)
        .hasMessage("simulated authorize failure");

    var secondSession = orderSessionService.createOrderSession(ownerLimitCommand(
        "123e4567-e89b-42d3-a456-426614174299",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    ));

    var verified = orderSessionService.verifyOtp(
        OrderSessionOtpVerifyCommand.of(ownerMember.getId(), secondSession.getOrderSessionId(), otpCode)
    );

    assertThat(verified.getStatus()).isEqualTo("AUTHED");
    assertThat(orderSessionRepository.findByOrderSessionId(secondSession.getOrderSessionId()))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.AUTHED));
  }

  @Test
  void shouldPersistRecoveryTransitionsWhenOrderSessionEntityIsDetached() {
    OrderSession persistedSession = orderSessionRepository.saveAndFlush(OrderSession.initiated(
        ownerMember.getId(),
        ownerMember.getAccountId(),
        "123e4567-e89b-42d3-a456-426614174399",
        ownerLimitCommand("123e4567-e89b-42d3-a456-426614174399", BigDecimal.ONE, BigDecimal.valueOf(70000)).replayFingerprint(),
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(70000),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.now().plus(Duration.ofHours(1))
    ));
    persistedSession.startExecuting();
    orderSessionRepository.saveAndFlush(persistedSession);

    OrderSession detachedExecuting = loadSessionInSeparateTransaction(persistedSession.getOrderSessionId());

    OrderSession requerying = orderSessionService.beginRequerying(
        detachedExecuting,
        "UNKNOWN_EXECUTION_OUTCOME",
        "FILLED",
        BigDecimal.ONE,
        BigDecimal.ZERO,
        BigDecimal.valueOf(70000),
        "FEP-DETACHED-1",
        "FAILED",
        Instant.parse("2026-03-18T00:00:00Z")
    );

    assertThat(orderSessionRepository.findByOrderSessionId(persistedSession.getOrderSessionId()))
        .hasValueSatisfying(session -> {
          assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.REQUERYING);
          assertThat(session.getFailureReason()).isEqualTo("UNKNOWN_EXECUTION_OUTCOME");
          assertThat(session.getExecutionResult()).isEqualTo("FILLED");
          assertThat(session.getExecutedQty()).isEqualByComparingTo("1");
          assertThat(session.getExternalOrderId()).isEqualTo("FEP-DETACHED-1");
          assertThat(session.getExternalSyncStatus()).isEqualTo("FAILED");
        });

    OrderSession detachedRequerying = loadSessionInSeparateTransaction(requerying.getOrderSessionId());

    orderSessionService.completeExecution(
        detachedRequerying,
        "FILLED",
        BigDecimal.ONE,
        BigDecimal.ZERO,
        BigDecimal.valueOf(70000),
        "FEP-DETACHED-1",
        "CONFIRMED",
        Instant.parse("2026-03-18T00:01:00Z")
    );

    assertThat(orderSessionRepository.findByOrderSessionId(persistedSession.getOrderSessionId()))
        .hasValueSatisfying(session -> {
          assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.COMPLETED);
          assertThat(session.getFailureReason()).isNull();
          assertThat(session.getExecutionResult()).isEqualTo("FILLED");
          assertThat(session.getExecutedQty()).isEqualByComparingTo("1");
          assertThat(session.getExternalSyncStatus()).isEqualTo("CONFIRMED");
          assertThat(session.getExecutedAt()).isEqualTo(Instant.parse("2026-03-18T00:01:00Z"));
        });
  }

  private Member saveLinkedMember(String memberNo, String email, String name, Long accountId, String accountNumber) {
    Member member = Member.registerUser(memberNo, email, "{noop}", name);
    member.updateLinkedAccount(accountId, accountNumber);
    return memberRepository.saveAndFlush(member);
  }

  private OrderSessionCreateCommand ownerLimitCommand(String clOrdId, BigDecimal qty, BigDecimal price) {
    return OrderSessionCreateCommand.of(
        ownerMember.getId(),
        ownerMember.getAccountId(),
        clOrdId,
        "005930",
        "BUY",
        "LIMIT",
        qty,
        price
    );
  }

  private OrderSession loadSessionInSeparateTransaction(String orderSessionId) {
    return requiresNewTransactionTemplate.execute(status -> orderSessionRepository.findByOrderSessionId(orderSessionId)
        .orElseThrow());
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    @Primary
    InMemoryOrderSessionTtlStore orderSessionTtlStore() {
      return new InMemoryOrderSessionTtlStore();
    }

    @Bean
    @Primary
    FaultInjectingOrderSessionPersistenceService testOrderSessionPersistenceService(
        ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository,
        OrderSessionRepository orderSessionRepository,
        AuditLogService auditLogService,
        Clock clock,
        PlatformTransactionManager transactionManager,
        InMemoryOrderSessionTtlStore orderSessionTtlStore
    ) {
      return new FaultInjectingOrderSessionPersistenceService(
          manualRecoveryQueueEntryRepository,
          orderSessionRepository,
          auditLogService,
          clock,
          transactionManager,
          orderSessionTtlStore
      );
    }

    @Bean
    @Primary
    RecordingOrderSessionRateLimitService testOrderSessionRateLimitService() {
      return new RecordingOrderSessionRateLimitService();
    }

    @Bean
    @Primary
    StubAccountPositionService stubAccountPositionService() {
      return new StubAccountPositionService();
    }
  }

  static class InMemoryOrderSessionTtlStore implements OrderSessionTtlStore {

    private static final Duration TTL = Duration.ofMinutes(60);

    private final Map<String, Instant> activeSessions = new ConcurrentHashMap<>();
    private volatile boolean failNextActivation;
    private volatile boolean dropTtlOnNextActivation;

    @Override
    public void activate(String orderSessionId, Instant expiresAt) {
      if (failNextActivation) {
        failNextActivation = false;
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "order session cache unavailable");
      }
      if (dropTtlOnNextActivation) {
        dropTtlOnNextActivation = false;
        activeSessions.remove(orderSessionId);
        return;
      }
      activeSessions.put(orderSessionId, expiresAt);
    }

    @Override
    public boolean isActive(String orderSessionId) {
      Instant expiresAt = activeSessions.get(orderSessionId);
      return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    @Override
    public void refresh(String orderSessionId, Instant expiresAt) {
      if (!activeSessions.containsKey(orderSessionId)) {
        throw new BusinessException(ErrorCode.ORDER_SESSION_NOT_FOUND, "Order session not found.");
      }
      activeSessions.put(orderSessionId, expiresAt);
    }

    @Override
    public void clear(String orderSessionId) {
      activeSessions.remove(orderSessionId);
    }

    @Override
    public Duration ttl() {
      return TTL;
    }

    void failNextActivation() {
      this.failNextActivation = true;
    }

    void dropTtlOnNextActivation() {
      this.dropTtlOnNextActivation = true;
    }

    void forceExpiry(String orderSessionId, Instant expiresAt) {
      activeSessions.put(orderSessionId, expiresAt);
    }

    void reset() {
      activeSessions.clear();
      failNextActivation = false;
      dropTtlOnNextActivation = false;
    }
  }

  static class FaultInjectingOrderSessionPersistenceService extends OrderSessionPersistenceService {

    private volatile boolean failNextCreateWithDuplicateAfterInsert;
    private volatile boolean failNextCreateWithDuplicateWithoutInsert;
    private volatile boolean failNextAuthorize;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final InMemoryOrderSessionTtlStore orderSessionTtlStore;

    FaultInjectingOrderSessionPersistenceService(
        ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository,
        OrderSessionRepository orderSessionRepository,
        AuditLogService auditLogService,
        Clock clock,
        PlatformTransactionManager transactionManager,
        InMemoryOrderSessionTtlStore orderSessionTtlStore
    ) {
      super(manualRecoveryQueueEntryRepository, orderSessionRepository, auditLogService, clock);
      this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
      this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      this.orderSessionTtlStore = orderSessionTtlStore;
    }

    @Override
    public OrderSession createSession(
        OrderSessionCreateCommand command,
        boolean challengeRequired,
        String authorizationReason,
        Instant expiresAt,
        String quoteSnapshotId,
        Instant quoteAsOf,
        FepQuoteSourceMode quoteSourceMode,
        BigDecimal preTradePrice
    ) {
      if (failNextCreateWithDuplicateWithoutInsert) {
        failNextCreateWithDuplicateWithoutInsert = false;
        throw new DataIntegrityViolationException("simulated duplicate create race without persisted session");
      }
      if (failNextCreateWithDuplicateAfterInsert) {
        failNextCreateWithDuplicateAfterInsert = false;
        OrderSession concurrentSession =
            requiresNewTransactionTemplate.execute(
                status -> super.createSession(
                    command,
                    challengeRequired,
                    authorizationReason,
                    expiresAt,
                    quoteSnapshotId,
                    quoteAsOf,
                    quoteSourceMode,
                    preTradePrice
                )
            );
        if (concurrentSession == null) {
          throw new IllegalStateException("simulated duplicate create race did not persist a session");
        }
        orderSessionTtlStore.activate(concurrentSession.getOrderSessionId(), concurrentSession.getExpiresAt());
        throw new DataIntegrityViolationException("simulated duplicate create race");
      }
      return super.createSession(
          command,
          challengeRequired,
          authorizationReason,
          expiresAt,
          quoteSnapshotId,
          quoteAsOf,
          quoteSourceMode,
          preTradePrice
      );
    }

    @Override
    OrderSession markAuthorized(OrderSession session) {
      if (failNextAuthorize) {
        failNextAuthorize = false;
        throw new IllegalStateException("simulated authorize failure");
      }
      return super.markAuthorized(session);
    }

    void failNextCreateWithDuplicateAfterInsert() {
      this.failNextCreateWithDuplicateAfterInsert = true;
    }

    void failNextCreateWithDuplicateWithoutInsert() {
      this.failNextCreateWithDuplicateWithoutInsert = true;
    }

    void failNextAuthorize() {
      this.failNextAuthorize = true;
    }

    void reset() {
      failNextCreateWithDuplicateAfterInsert = false;
      failNextCreateWithDuplicateWithoutInsert = false;
      failNextAuthorize = false;
    }
  }

  static class RecordingOrderSessionRateLimitService extends OrderSessionRateLimitService {

    private final Map<Long, Integer> enforcementCounts = new ConcurrentHashMap<>();
    private final Map<Long, Integer> refundCounts = new ConcurrentHashMap<>();

    RecordingOrderSessionRateLimitService() {
      super(new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class));
    }

    @Override
    public void enforceCreateRateLimit(Long memberId) {
      enforcementCounts.merge(memberId, 1, Integer::sum);
    }

    @Override
    public void refundCreateRateLimit(Long memberId) {
      refundCounts.merge(memberId, 1, Integer::sum);
    }

    int enforcementCount(Long memberId) {
      return enforcementCounts.getOrDefault(memberId, 0);
    }

    int refundCount(Long memberId) {
      return refundCounts.getOrDefault(memberId, 0);
    }

    void reset() {
      enforcementCounts.clear();
      refundCounts.clear();
    }
  }

  static class StubAccountPositionService extends AccountPositionService {

    private Long accountId = 0L;
    private Long memberId = 0L;
    private BigDecimal availableBalance = BigDecimal.valueOf(5_000_000);
    private BigDecimal availableQuantity = BigDecimal.valueOf(500);
    private BigDecimal marketPrice = BigDecimal.valueOf(72050).setScale(4);
    private String quoteSnapshotId = "qsnap_005930_live_001";
    private Instant quoteAsOf = Instant.parse("2026-03-20T00:00:00Z");
    private FepQuoteSourceMode quoteSourceMode = FepQuoteSourceMode.LIVE;
    private RuntimeException failure;

    StubAccountPositionService() {
      super(null);
    }

    @Override
    public AccountPositionResult getAccountSummary(AccountSummaryQueryCommand command) {
      return AccountPositionResult.of(
          command.getAccountId(),
          command.getMemberId(),
          "",
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          availableBalance,
          "KRW",
          Instant.now()
      );
    }

    @Override
    public AccountPositionResult getAccountPosition(AccountPositionQueryCommand command) {
      if (failure != null) {
        RuntimeException nextFailure = failure;
        failure = null;
        throw nextFailure;
      }
      return AccountPositionResult.of(
          command.getAccountId(),
          command.getMemberId(),
          command.getSymbol(),
          availableQuantity,
          availableQuantity,
          availableBalance,
          "KRW",
          Instant.now(),
          marketPrice,
          quoteSnapshotId,
          quoteAsOf,
          quoteSourceMode
      );
    }

    void reset(Long accountId, Long memberId) {
      this.accountId = accountId;
      this.memberId = memberId;
      this.availableBalance = BigDecimal.valueOf(5_000_000);
      this.availableQuantity = BigDecimal.valueOf(500);
      this.marketPrice = BigDecimal.valueOf(72050).setScale(4);
      this.quoteSnapshotId = "qsnap_005930_live_001";
      this.quoteAsOf = Instant.parse("2026-03-20T00:00:00Z");
      this.quoteSourceMode = FepQuoteSourceMode.LIVE;
      this.failure = null;
    }

    void setAvailableBalance(BigDecimal availableBalance) {
      this.availableBalance = availableBalance;
    }

    void setAvailableQuantity(BigDecimal availableQuantity) {
      this.availableQuantity = availableQuantity;
    }

    void failNextWith(RuntimeException failure) {
      this.failure = failure;
    }
  }
}
