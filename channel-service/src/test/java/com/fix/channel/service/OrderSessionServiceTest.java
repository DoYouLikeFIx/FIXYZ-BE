package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.channel.entity.Member;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.channel.vo.OrderSessionQueryCommand;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
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
  private InMemoryOrderSessionTtlStore orderSessionTtlStore;

  @Autowired
  private FaultInjectingOrderSessionPersistenceService orderSessionPersistenceService;

  @Autowired
  private RecordingOrderSessionRateLimitService orderSessionRateLimitService;

  private Member ownerMember;
  private Member otherMember;

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAll();
    orderSessionRepository.deleteAll();
    memberRepository.deleteAll();
    orderSessionTtlStore.reset();
    orderSessionPersistenceService.reset();
    orderSessionRateLimitService.reset();
    ownerMember = saveLinkedMember("M-ORD-SVC-001", "svc-owner@fixyz.com", "Service Owner", 101L, "12345678901234");
    otherMember = saveLinkedMember("M-ORD-SVC-002", "svc-other@fixyz.com", "Service Other", 202L, "43210987654321");
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
    assertThat(result.getAccountId()).isEqualTo(101L);
    assertThat(result.getSymbol()).isEqualTo("005930");
    assertThat(result.getSide()).isEqualTo("BUY");
    assertThat(result.getOrderType()).isEqualTo("LIMIT");
    assertThat(result.getQty()).isEqualByComparingTo("10");
    assertThat(result.getPrice()).isEqualByComparingTo("72000");
    assertThat(result.getRemainingSeconds()).isBetween(1L, 600L);
    assertThat(result.getExpiresAt()).isNotNull();
    assertThat(result.getCreatedAt()).isNotNull();
    assertThat(result.getUpdatedAt()).isNotNull();
    assertThat(auditLogRepository.count()).isEqualTo(1L);
    assertThat(orderSessionRepository.findByOrderSessionId(result.getOrderSessionId()))
        .hasValueSatisfying(session -> {
          assertThat(session.getExpiresAt()).isEqualTo(result.getExpiresAt());
          assertThat(session.getAccountId()).isEqualTo(101L);
          assertThat(session.getSymbol()).isEqualTo("005930");
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
    assertThat(auditLogRepository.count()).isEqualTo(1L);
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
    assertThat(auditLogRepository.count()).isZero();
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
    assertThat(orderSessionRateLimitService.enforcementCount(ownerMember.getId())).isEqualTo(1);
    assertThat(orderSessionRateLimitService.refundCount(ownerMember.getId())).isEqualTo(1);
    assertThat(auditLogRepository.count()).isEqualTo(1L);
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
        OrderSessionRepository orderSessionRepository,
        AuditLogRepository auditLogRepository,
        PlatformTransactionManager transactionManager,
        InMemoryOrderSessionTtlStore orderSessionTtlStore
    ) {
      return new FaultInjectingOrderSessionPersistenceService(
          orderSessionRepository,
          auditLogRepository,
          transactionManager,
          orderSessionTtlStore
      );
    }

    @Bean
    @Primary
    RecordingOrderSessionRateLimitService testOrderSessionRateLimitService() {
      return new RecordingOrderSessionRateLimitService();
    }
  }

  static class InMemoryOrderSessionTtlStore implements OrderSessionTtlStore {

    private static final Duration TTL = Duration.ofMinutes(10);

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
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final InMemoryOrderSessionTtlStore orderSessionTtlStore;

    FaultInjectingOrderSessionPersistenceService(
        OrderSessionRepository orderSessionRepository,
        AuditLogRepository auditLogRepository,
        PlatformTransactionManager transactionManager,
        InMemoryOrderSessionTtlStore orderSessionTtlStore
    ) {
      super(orderSessionRepository, auditLogRepository);
      this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
      this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      this.orderSessionTtlStore = orderSessionTtlStore;
    }

    @Override
    public OrderSession createPendingNewSession(OrderSessionCreateCommand command, Instant expiresAt) {
      if (failNextCreateWithDuplicateAfterInsert) {
        failNextCreateWithDuplicateAfterInsert = false;
        OrderSession concurrentSession =
            requiresNewTransactionTemplate.execute(status -> super.createPendingNewSession(command, expiresAt));
        if (concurrentSession == null) {
          throw new IllegalStateException("simulated duplicate create race did not persist a session");
        }
        orderSessionTtlStore.activate(concurrentSession.getOrderSessionId(), concurrentSession.getExpiresAt());
        throw new DataIntegrityViolationException("simulated duplicate create race");
      }
      return super.createPendingNewSession(command, expiresAt);
    }

    void failNextCreateWithDuplicateAfterInsert() {
      this.failNextCreateWithDuplicateAfterInsert = true;
    }

    void reset() {
      failNextCreateWithDuplicateAfterInsert = false;
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
}
