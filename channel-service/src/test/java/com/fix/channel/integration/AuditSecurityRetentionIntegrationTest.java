package com.fix.channel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.service.AuditSecurityRetentionService;
import com.fix.channel.service.SecurityEventService;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:channel_audit_security_retention;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class AuditSecurityRetentionIntegrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private AuditLogRepository auditLogRepository;

  @Autowired
  private SecurityEventRepository securityEventRepository;

  @Autowired
  private AuditSecurityRetentionService auditSecurityRetentionService;

  @Autowired
  private SecurityEventService securityEventService;

  @BeforeEach
  void setUp() {
    securityEventRepository.deleteAll();
    auditLogRepository.deleteAll();
  }

  @Test
  void shouldPurgeOnlyRowsOlderThanRetentionBoundaries() {
    Instant now = Instant.now();
    insertAuditLog("audit-old", now.minus(Duration.ofDays(91)));
    insertAuditLog("audit-fresh", now.minus(Duration.ofDays(10)));
    insertSecurityEvent("security-old", now.minus(Duration.ofDays(181)));
    insertSecurityEvent("security-fresh", now.minus(Duration.ofDays(10)));

    AuditSecurityRetentionService.CleanupResult result = auditSecurityRetentionService.runCleanupCycle();

    assertThat(result.deletedAuditLogs()).isEqualTo(1);
    assertThat(result.deletedSecurityEvents()).isEqualTo(1);
    assertThat(auditLogRepository.findAll())
        .extracting(log -> log.getAuditUuid())
        .containsExactly("audit-fresh");
    assertThat(securityEventRepository.findAll())
        .extracting(SecurityEvent::getSecurityEventUuid)
        .containsExactly("security-fresh");
  }

  @Test
  void shouldPersistAdminActorIdentityForPrivilegedSecurityAction() {
    SecurityEvent saved = securityEventService.record(SecurityEvent.of(
        101L,
        "FORCED_LOGOUT",
        "127.0.0.1",
        "JUnit",
        "HIGH"
    ).withAdminMemberId(900L)
        .withOrderSessionId(777L)
        .withDetail("reason=admin_force_logout")
        .withCorrelationId("123e4567-e89b-42d3-a456-426614174299"));

    SecurityEvent persisted = securityEventRepository.findById(saved.getId()).orElseThrow();

    assertThat(persisted.getSecurityEventUuid()).isNotBlank();
    assertThat(persisted.getAdminMemberId()).isEqualTo(900L);
    assertThat(persisted.getOrderSessionId()).isEqualTo(777L);
    assertThat(persisted.getStatus()).isEqualTo("OPEN");
    assertThat(persisted.getOccurredAt()).isNotNull();
    assertThat(persisted.getDetail()).isEqualTo("reason=admin_force_logout");
  }

  @Test
  void shouldPopulateBaseAndCanonicalTimestampsOnPersist() {
    AuditLog savedAuditLog = auditLogRepository.saveAndFlush(AuditLog.ofOrderSession(
        101L,
        777L,
        "ORDER_SESSION_CREATE",
        "ORDER_SESSION",
        "123e4567-e89b-42d3-a456-426614174260",
        "clOrdId=123e4567-e89b-42d3-a456-426614174260",
        "127.0.0.1",
        "JUnit",
        "123e4567-e89b-42d3-a456-426614174299"
    ));
    SecurityEvent savedSecurityEvent = securityEventRepository.saveAndFlush(SecurityEvent.of(
        101L,
        "ACCOUNT_LOCKED",
        "127.0.0.1",
        "JUnit",
        "HIGH"
    ).withCorrelationId("123e4567-e89b-42d3-a456-426614174299"));

    assertThat(savedAuditLog.getAuditUuid()).isNotBlank();
    assertThat(savedAuditLog.getCreatedAt()).isNotNull();
    assertThat(savedAuditLog.getUpdatedAt()).isNotNull();
    assertThat(savedSecurityEvent.getSecurityEventUuid()).isNotBlank();
    assertThat(savedSecurityEvent.getOccurredAt()).isNotNull();
    assertThat(savedSecurityEvent.getCreatedAt()).isNotNull();
    assertThat(savedSecurityEvent.getUpdatedAt()).isNotNull();
  }

  private void insertAuditLog(String auditUuid, Instant createdAt) {
    jdbcTemplate.update("""
        INSERT INTO audit_logs(
          audit_uuid, member_id, action, target_type, target_id, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        auditUuid,
        101L,
        "AUTH_LOGIN_SUCCESS",
        "MEMBER",
        "101",
        Timestamp.from(createdAt),
        Timestamp.from(createdAt),
        0L
    );
  }

  private void insertSecurityEvent(String securityEventUuid, Instant occurredAt) {
    jdbcTemplate.update("""
        INSERT INTO security_events(
          security_event_uuid, member_id, event_type, status, severity, occurred_at, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        securityEventUuid,
        101L,
        "ACCOUNT_LOCKED",
        "OPEN",
        "HIGH",
        Timestamp.from(occurredAt),
        Timestamp.from(occurredAt),
        Timestamp.from(occurredAt),
        0L
    );
  }
}
