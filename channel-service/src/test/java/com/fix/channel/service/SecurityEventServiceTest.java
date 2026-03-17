package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.SecurityEventRepository;
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
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class SecurityEventServiceTest {

  @Mock
  private SecurityEventRepository securityEventRepository;

  @Mock
  private PlatformTransactionManager transactionManager;

  private SecurityEventService securityEventService;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
    securityEventService = new SecurityEventService(securityEventRepository, transactionManager);
  }

  @Test
  void shouldLogFailureContextWhenSecurityEventInsertFails(CapturedOutput output) {
    SecurityEvent securityEvent = SecurityEvent.of(
        101L,
        "ACCOUNT_LOCKED",
        "127.0.0.1",
        "JUnit",
        "HIGH"
    ).withAdminMemberId(900L).withDetail("reason=force_logout").withCorrelationId("123e4567-e89b-42d3-a456-426614174299");

    when(securityEventRepository.saveAndFlush(any(SecurityEvent.class)))
        .thenThrow(new DataIntegrityViolationException("simulated security event failure"));

    SecurityEvent recorded = securityEventService.record(securityEvent);

    assertThat(recorded).isSameAs(securityEvent);
    assertThat(output)
        .contains("Failed to persist security event")
        .contains("ACCOUNT_LOCKED")
        .contains("900");
  }
}
