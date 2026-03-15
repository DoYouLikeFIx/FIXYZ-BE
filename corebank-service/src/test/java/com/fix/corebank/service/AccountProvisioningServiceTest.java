package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.MemberRepository;
import com.fix.corebank.vo.AccountProvisioningCommand;
import com.fix.corebank.vo.AccountProvisioningResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_provisioning;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "corebank.provisioning.default-cash-balance=100000000.0000"
})
class AccountProvisioningServiceTest {

  @Autowired
  private AccountProvisioningService accountProvisioningService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private MemberRepository memberRepository;

  @Test
  void shouldProvisionDefaultAccountWhenCalledFirstTime() {
    AccountProvisioningResult result = accountProvisioningService.provisionDefaultAccount(
        AccountProvisioningCommand.of(101L, "M-101", "member101@fix.local")
    );

    assertThat(result.isIdempotent()).isFalse();
    assertThat(result.getStatus()).isEqualTo("ACTIVE");
    assertThat(result.getAccountNumber()).isEqualTo("11000000000101");
    assertThat(accountRepository.findByMemberId(101L)).hasValueSatisfying((account) -> {
      assertThat(account.getCashBalance()).isEqualByComparingTo(new BigDecimal("100000000.0000"));
    });
    assertThat(memberRepository.findById(101L)).isPresent();
  }

  @Test
  void shouldReturnExistingAccountWhenProvisioningRequestIsDuplicated() {
    AccountProvisioningCommand command = AccountProvisioningCommand.of(102L, "M-102", "member102@fix.local");

    AccountProvisioningResult first = accountProvisioningService.provisionDefaultAccount(command);
    AccountProvisioningResult second = accountProvisioningService.provisionDefaultAccount(command);

    assertThat(first.getAccountId()).isEqualTo(second.getAccountId());
    assertThat(first.isIdempotent()).isFalse();
    assertThat(second.isIdempotent()).isTrue();
  }

  @Test
  void shouldRollbackMemberWriteWhenAccountCreationValidationFails() {
    Long oversizedMemberId = 1_000_000_000_000L;

    assertThatThrownBy(() -> accountProvisioningService.provisionDefaultAccount(
        AccountProvisioningCommand.of(oversizedMemberId, null, null)
    ))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);

    assertThat(memberRepository.findById(oversizedMemberId)).isEmpty();
    assertThat(accountRepository.findByMemberId(oversizedMemberId)).isEmpty();
  }

  @Test
  void shouldReturnValidationCodeWhenMemberUpsertConflicts() {
    assertThatThrownBy(() -> accountProvisioningService.provisionDefaultAccount(
        AccountProvisioningCommand.of(103L, "M-103", "seed@fix.local")
    ))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);

    assertThat(accountRepository.findByMemberId(103L)).isEmpty();
  }
}
