package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.channel.vo.OrderSessionQueryCommand;
import com.fix.common.error.BusinessException;
import java.util.Map;
import java.util.Optional;
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
  private OrderSessionRepository orderSessionRepository;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Autowired
  private InMemoryOrderSessionTtlStore orderSessionTtlStore;

  @Autowired
  private FaultInjectingOrderSessionPersistenceService orderSessionPersistenceService;

  @Autowired
  private RecordingOrderSessionRateLimitService orderSessionRateLimitService;

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAll();
    orderSessionRepository.deleteAll();
    orderSessionTtlStore.reset();
    orderSessionPersistenceService.reset();
    orderSessionRateLimitService.reset();
  }

  @Test
  void shouldCreatePendingNewSessionWithTtlMetadata() {
    var result = orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174260", "ORD-REF-001")
    );

    assertThat(result.isCreated()).isTrue();
    assertThat(result.getOrderSessionId()).isNotBlank();
    assertThat(result.getStatus()).isEqualTo("PENDING_NEW");
    assertThat(result.getRemainingSeconds()).isEqualTo(600L);
    assertThat(result.getExpiresAt()).isNotNull();
    assertThat(auditLogRepository.count()).isEqualTo(1L);
  }

  @Test
  void shouldReturnReplayForExistingActiveSession() {
    var created = orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174261", "ORD-REF-002")
    );

    var replayed = orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174261", "ORD-REF-002")
    );

    assertThat(replayed.isCreated()).isFalse();
    assertThat(replayed.getOrderSessionId()).isEqualTo(created.getOrderSessionId());
    assertThat(replayed.getExpiresAt()).isEqualTo(created.getExpiresAt());
    assertThat(auditLogRepository.count()).isEqualTo(1L);
  }

  @Test
  void shouldRejectReplayWhenRequestPayloadDiffers() {
    orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174262", "ORD-REF-003")
    );

    assertThatThrownBy(() -> orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174262", "ORD-REF-CHANGED")
    ))
        .isInstanceOf(BusinessException.class)
        .hasMessage("clOrdId replay payload mismatch");
  }

  @Test
  void shouldCleanupPersistedSessionWhenTtlActivationFails() {
    orderSessionTtlStore.failNextActivation();

    assertThatThrownBy(() -> orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174263", "ORD-REF-004")
    ))
        .isInstanceOf(BusinessException.class)
        .hasMessage("order session cache unavailable");

    assertThat(orderSessionRepository.findByClOrdId("123e4567-e89b-42d3-a456-426614174263")).isEmpty();
    assertThat(auditLogRepository.count()).isZero();
  }

  @Test
  void shouldReturnNotFoundWhenFreshCreateLosesTtlBeforeResponseBuild() {
    orderSessionTtlStore.dropTtlOnNextActivation();

    assertThatThrownBy(() -> orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174266", "ORD-REF-004B")
    ))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Order session not found.");

    assertThat(orderSessionRepository.findByClOrdId("123e4567-e89b-42d3-a456-426614174266"))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.EXPIRED));
  }

  @Test
  void shouldExpireSessionWhenLookupFindsNoLiveTtl() {
    var created = orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174264", "ORD-REF-005")
    );
    orderSessionTtlStore.clear(created.getOrderSessionId());

    assertThatThrownBy(() -> orderSessionService.getOrderSession(
        OrderSessionQueryCommand.of(301L, created.getOrderSessionId(), null)
    ))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Order session not found.");

    assertThat(orderSessionRepository.findByOrderSessionId(created.getOrderSessionId()))
        .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.EXPIRED));
  }

  @Test
  void shouldTreatExpiredReplayAsNotFoundBeforePayloadValidation() {
    var created = orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174265", "ORD-REF-006")
    );
    orderSessionTtlStore.clear(created.getOrderSessionId());

    assertThatThrownBy(() -> orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174265", "ORD-REF-CHANGED")
    ))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Order session not found.");
  }

  @Test
  void shouldRecoverConcurrentDuplicateExceptionAndRefundRateLimit() {
    orderSessionPersistenceService.failNextCreateWithDuplicateAfterInsert();

    var replayed = orderSessionService.createOrderSession(
        OrderSessionCreateCommand.of(301L, "123e4567-e89b-42d3-a456-426614174267", "ORD-REF-007")
    );

    assertThat(replayed.isCreated()).isFalse();
    assertThat(replayed.getOrderSessionId()).isNotBlank();
    assertThat(replayed.getStatus()).isEqualTo("PENDING_NEW");
    assertThat(orderSessionRateLimitService.enforcementCount(301L)).isEqualTo(1);
    assertThat(orderSessionRateLimitService.refundCount(301L)).isEqualTo(1);
    assertThat(auditLogRepository.count()).isEqualTo(1L);
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

    private static final long TTL_SECONDS = 600L;

    private final Map<String, Long> remainingSeconds = new ConcurrentHashMap<>();
    private volatile boolean failNextActivation;
    private volatile boolean dropTtlOnNextActivation;

    @Override
    public void activate(String orderSessionId) {
      if (failNextActivation) {
        failNextActivation = false;
        throw new BusinessException(com.fix.common.error.ErrorCode.INTERNAL_ERROR, "order session cache unavailable");
      }
      if (dropTtlOnNextActivation) {
        dropTtlOnNextActivation = false;
        remainingSeconds.remove(orderSessionId);
        return;
      }
      remainingSeconds.put(orderSessionId, TTL_SECONDS);
    }

    @Override
    public Optional<Long> remainingSeconds(String orderSessionId) {
      return Optional.ofNullable(remainingSeconds.get(orderSessionId));
    }

    @Override
    public void clear(String orderSessionId) {
      remainingSeconds.remove(orderSessionId);
    }

    @Override
    public long ttlSeconds() {
      return TTL_SECONDS;
    }

    void failNextActivation() {
      this.failNextActivation = true;
    }

    void dropTtlOnNextActivation() {
      this.dropTtlOnNextActivation = true;
    }

    void reset() {
      remainingSeconds.clear();
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
    public OrderSession createPendingNewSession(OrderSessionCreateCommand command) {
      if (failNextCreateWithDuplicateAfterInsert) {
        failNextCreateWithDuplicateAfterInsert = false;
        OrderSession concurrentSession = requiresNewTransactionTemplate.execute(status -> super.createPendingNewSession(command));
        if (concurrentSession == null) {
          throw new IllegalStateException("simulated duplicate create race did not persist a session");
        }
        orderSessionTtlStore.activate(concurrentSession.getOrderSessionId());
        throw new DataIntegrityViolationException("simulated duplicate create race");
      }
      return super.createPendingNewSession(command);
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
