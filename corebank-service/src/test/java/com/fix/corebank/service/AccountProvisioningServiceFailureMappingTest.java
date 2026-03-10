package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fix.common.error.ErrorCode;
import com.fix.common.error.SystemException;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.MemberRepository;
import com.fix.corebank.vo.AccountProvisioningCommand;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AccountProvisioningServiceFailureMappingTest {

  @Mock
  private MemberRepository memberRepository;

  @Mock
  private AccountRepository accountRepository;

  @Mock
  private PlatformTransactionManager transactionManager;

  private AccountProvisioningService accountProvisioningService;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    accountProvisioningService = new AccountProvisioningService(memberRepository, accountRepository, transactionManager);
  }

  @Test
  void shouldMapUnexpectedFailureToCoreProvisioningErrorCode() {
    when(memberRepository.findById(201L)).thenReturn(Optional.empty());
    when(memberRepository.saveAndFlush(any())).thenThrow(new RuntimeException("unexpected boom"));

    assertThatThrownBy(() -> accountProvisioningService.provisionDefaultAccount(
        AccountProvisioningCommand.of(201L, "M-201", "member201@fix.local")
    ))
        .isInstanceOf(SystemException.class)
        .extracting(ex -> ((SystemException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CORE_PROVISIONING_FAILED);
  }
}
