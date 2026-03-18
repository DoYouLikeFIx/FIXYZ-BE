package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class AuditLogServiceTest {

  @Mock
  private AuditLogRepository auditLogRepository;

  @Mock
  private PlatformTransactionManager transactionManager;

  private AuditLogService auditLogService;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
    auditLogService = new AuditLogService(auditLogRepository, transactionManager);
  }

  @Test
  void shouldLogFailureContextWhenAuditInsertFails(CapturedOutput output) {
    AuditLog auditLog = AuditLog.ofOrderSession(
        101L,
        777L,
        AuditAction.ORDER_SESSION_CREATE,
        "ORDER_SESSION",
        "123e4567-e89b-42d3-a456-426614174260",
        "clOrdId=123e4567-e89b-42d3-a456-426614174260",
        "127.0.0.1",
        "JUnit",
        "123e4567-e89b-42d3-a456-426614174299"
    );
    when(auditLogRepository.saveAndFlush(any(AuditLog.class)))
        .thenThrow(new DataIntegrityViolationException("simulated audit failure"));

    AuditLog recorded = auditLogService.record(auditLog);

    assertThat(recorded).isSameAs(auditLog);
    assertThat(output)
        .contains("Failed to persist audit log")
        .contains("ORDER_SESSION_CREATE")
        .contains("123e4567-e89b-42d3-a456-426614174260");
  }
}
