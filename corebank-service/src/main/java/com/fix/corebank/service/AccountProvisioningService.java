package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.SystemException;
import com.fix.corebank.entity.Account;
import com.fix.corebank.entity.MemberEntity;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.MemberRepository;
import com.fix.corebank.vo.AccountProvisioningCommand;
import com.fix.corebank.vo.AccountProvisioningResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountProvisioningService {

  private static final String DEFAULT_CURRENCY = "KRW";
  private static final BigDecimal DEFAULT_CASH_BALANCE = new BigDecimal("0.0000");
  private static final BigDecimal DEFAULT_DAILY_SELL_LIMIT = new BigDecimal("500.0000");

  private final MemberRepository memberRepository;
  private final AccountRepository accountRepository;

  @Transactional
  public AccountProvisioningResult provisionDefaultAccount(AccountProvisioningCommand command) {
    validateCommand(command);
    try {
      MemberEntity member = upsertMember(command);
      return createOrReturnDefaultAccount(member);
    } catch (BusinessException | SystemException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new SystemException(ErrorCode.CORE_PROVISIONING_FAILED, "default account provisioning failed", ex);
    }
  }

  private MemberEntity upsertMember(AccountProvisioningCommand command) {
    try {
      return memberRepository.findById(command.getMemberId())
          .map(existing -> {
            String memberNo = normalizeMemberNo(command.getMemberId(), command.getMemberNo(), existing.getMemberNo());
            String email = normalizeEmail(command.getMemberId(), command.getEmail(), existing.getEmail());
            existing.updateProfile(memberNo, email);
            return memberRepository.saveAndFlush(existing);
          })
          .orElseGet(() -> memberRepository.saveAndFlush(MemberEntity.of(
              command.getMemberId(),
              normalizeMemberNo(command.getMemberId(), command.getMemberNo(), null),
              normalizeEmail(command.getMemberId(), command.getEmail(), null)
          )));
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "member upsert conflict", ex);
    }
  }

  private AccountProvisioningResult createOrReturnDefaultAccount(MemberEntity member) {
    return accountRepository.findByMemberId(member.getId())
        .map(existing -> toResult(existing, true))
        .orElseGet(() -> createDefaultAccount(member));
  }

  private AccountProvisioningResult createDefaultAccount(MemberEntity member) {
    Account account = Account.of(
        generateAccountNo(member.getId()),
        member.getId(),
        DEFAULT_CURRENCY,
        DEFAULT_CASH_BALANCE,
        DEFAULT_DAILY_SELL_LIMIT
    );

    try {
      Account saved = accountRepository.saveAndFlush(account);
      return toResult(saved, false);
    } catch (DataIntegrityViolationException ex) {
      return accountRepository.findByMemberId(member.getId())
          .map(existing -> toResult(existing, true))
          .orElseThrow(() -> new SystemException(
              ErrorCode.CORE_PROVISIONING_FAILED,
              "default account provisioning failed",
              ex
          ));
    }
  }

  private AccountProvisioningResult toResult(Account account, boolean idempotent) {
    Instant createdAt = account.getCreatedAt();
    return AccountProvisioningResult.of(
        account.getId(),
        account.getAccountNo(),
        account.getStatus(),
        idempotent,
        account.getMemberId(),
        createdAt
    );
  }

  private String generateAccountNo(Long memberId) {
    String memberIdPart = Long.toString(memberId);
    if (memberIdPart.length() > 12) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "memberId must be 12 digits or fewer for account provisioning"
      );
    }
    return "11" + "0".repeat(12 - memberIdPart.length()) + memberIdPart;
  }

  private void validateCommand(AccountProvisioningCommand command) {
    if (command == null || command.getMemberId() == null || command.getMemberId() <= 0) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "memberId is required");
    }
  }

  private String normalizeMemberNo(Long memberId, String requested, String existing) {
    String resolved = firstNonBlank(requested, existing, defaultMemberNo(memberId));
    if (resolved.length() > 64) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "memberNo length must be <= 64");
    }
    return resolved;
  }

  private String normalizeEmail(Long memberId, String requested, String existing) {
    String fallback = defaultEmail(memberId);
    String source = firstNonBlank(requested, existing, fallback);
    String resolved = source.toLowerCase(Locale.ROOT);
    if (resolved.length() > 128) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "email length must be <= 128");
    }
    return resolved;
  }

  private String firstNonBlank(String first, String second, String fallback) {
    if (first != null && !first.trim().isEmpty()) {
      return first.trim();
    }
    if (second != null && !second.trim().isEmpty()) {
      return second.trim();
    }
    return fallback;
  }

  private String defaultMemberNo(Long memberId) {
    return "M-" + memberId;
  }

  private String defaultEmail(Long memberId) {
    return "member-" + memberId + "@fix.local";
  }
}
