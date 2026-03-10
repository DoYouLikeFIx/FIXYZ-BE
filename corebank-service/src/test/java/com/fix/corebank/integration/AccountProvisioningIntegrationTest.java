package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.entity.MemberEntity;
import com.fix.corebank.repository.MemberRepository;
import com.fix.corebank.service.AccountProvisioningService;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.vo.AccountProvisioningCommand;
import com.fix.corebank.vo.AccountProvisioningResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.locations=classpath:db/migration-provisioning-it"
})
class AccountProvisioningIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final AtomicLong MEMBER_ID_SEQUENCE = new AtomicLong(100_000L);

  @Autowired
  private AccountProvisioningService accountProvisioningService;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldCreateDefaultAccountOnFirstProvisioningCall() {
    long memberId = nextMemberId();

    AccountProvisioningResult result = accountProvisioningService.provisionDefaultAccount(
        AccountProvisioningCommand.of(memberId, "M-" + memberId, "member-" + memberId + "@fix.local")
    );

    assertThat(result.isIdempotent()).isFalse();
    assertThat(result.getMemberId()).isEqualTo(memberId);
    assertThat(result.getStatus()).isEqualTo("ACTIVE");
    assertThat(countAccountsByMemberId(memberId)).isEqualTo(1);
  }

  @Test
  void shouldReturnExistingAccountWhenProvisioningIsRetried() {
    long memberId = nextMemberId();
    AccountProvisioningCommand command = AccountProvisioningCommand.of(
        memberId,
        "M-" + memberId,
        "member-" + memberId + "@fix.local"
    );

    AccountProvisioningResult first = accountProvisioningService.provisionDefaultAccount(command);
    AccountProvisioningResult second = accountProvisioningService.provisionDefaultAccount(command);

    assertThat(first.getAccountId()).isEqualTo(second.getAccountId());
    assertThat(first.isIdempotent()).isFalse();
    assertThat(second.isIdempotent()).isTrue();
    assertThat(countAccountsByMemberId(memberId)).isEqualTo(1);
  }

  @Test
  void shouldHandleConcurrentDuplicateProvisioningWithSingleAccountOutcome() throws Exception {
    long memberId = nextMemberId();
    memberRepository.saveAndFlush(MemberEntity.of(memberId, "M-" + memberId, "member-" + memberId + "@fix.local"));

    AccountProvisioningCommand command = AccountProvisioningCommand.of(
        memberId,
        "M-" + memberId,
        "member-" + memberId + "@fix.local"
    );
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executorService = Executors.newFixedThreadPool(2);

    Callable<AccountProvisioningResult> task = () -> {
      ready.countDown();
      start.await(3, TimeUnit.SECONDS);
      return accountProvisioningService.provisionDefaultAccount(command);
    };

    Future<AccountProvisioningResult> firstFuture = executorService.submit(task);
    Future<AccountProvisioningResult> secondFuture = executorService.submit(task);

    assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
    start.countDown();

    AccountProvisioningResult first = firstFuture.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
    AccountProvisioningResult second = secondFuture.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
    executorService.shutdownNow();

    assertThat(first.getAccountId()).isEqualTo(second.getAccountId());
    assertThat(List.of(first.isIdempotent(), second.isIdempotent())).containsExactlyInAnyOrder(false, true);
    assertThat(countAccountsByMemberId(memberId)).isEqualTo(1);
  }

  @Test
  void shouldRollbackWithoutPartialWriteWhenProvisioningFails() {
    long memberId = nextMemberId();
    memberRepository.saveAndFlush(MemberEntity.of(1L, "M-SEED", "seed@fix.local"));

    assertThatThrownBy(() -> accountProvisioningService.provisionDefaultAccount(
        AccountProvisioningCommand.of(memberId, "M-" + memberId, "seed@fix.local")
    ))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);

    assertThat(memberRepository.findById(memberId)).isEmpty();
    assertThat(countAccountsByMemberId(memberId)).isEqualTo(0);
  }

  private long nextMemberId() {
    return MEMBER_ID_SEQUENCE.incrementAndGet();
  }

  private int countAccountsByMemberId(long memberId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM accounts WHERE member_id = ?",
        Integer.class,
        memberId
    );
    return count == null ? 0 : count;
  }
}
