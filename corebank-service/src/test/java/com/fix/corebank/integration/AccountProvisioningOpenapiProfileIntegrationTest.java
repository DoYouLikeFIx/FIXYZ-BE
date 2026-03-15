package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.corebank.entity.Account;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.MemberRepository;
import com.fix.corebank.service.AccountProvisioningService;
import com.fix.corebank.vo.AccountProvisioningCommand;
import com.fix.corebank.vo.AccountProvisioningResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("openapi")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_openapi_profile_it;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class AccountProvisioningOpenapiProfileIntegrationTest {

  @Autowired
  private AccountProvisioningService accountProvisioningService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private MemberRepository memberRepository;

  @Test
  void shouldProvisionIsolatedAccountForChannelMemberWithoutReusingSeededFixtures() {
    AccountProvisioningResult result = accountProvisioningService.provisionDefaultAccount(
        AccountProvisioningCommand.of(1L, "M-1", "member-1@fix.local")
    );

    Account provisionedAccount = accountRepository.findByMemberId(1L).orElseThrow();

    assertThat(result.isIdempotent()).isFalse();
    assertThat(result.getMemberId()).isEqualTo(1L);
    assertThat(result.getAccountId()).isEqualTo(provisionedAccount.getId());
    assertThat(result.getAccountId()).isNotEqualTo(900001L);
    assertThat(result.getAccountNumber()).isEqualTo("11000000000001");
    assertThat(provisionedAccount.getCashBalance()).isEqualByComparingTo(new BigDecimal("100000000.0000"));
    assertThat(memberRepository.findById(1L)).hasValueSatisfying((member) -> {
      assertThat(member.getEmail()).isEqualTo("member-1@fix.local");
      assertThat(member.getMemberNo()).isEqualTo("M-1");
    });
    assertThat(accountRepository.findByMemberId(900001L)).hasValueSatisfying((seededAccount) -> {
      assertThat(seededAccount.getId()).isEqualTo(900001L);
      assertThat(seededAccount.getAccountNo()).isEqualTo("110123456789");
    });
  }
}
