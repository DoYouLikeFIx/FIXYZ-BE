package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.entity.Account;
import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.AccountRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.vo.AccountPositionQueryCommand;
import com.fix.corebank.vo.AccountPositionsQueryCommand;
import com.fix.corebank.vo.AccountStatusQueryCommand;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CorebankAccountPositionQueryService {

  private final AccountRepository accountRepository;
  private final PositionRepository positionRepository;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AccountPositionSnapshot getOwnedAccountPosition(AccountPositionQueryCommand command) {
    Account account = getOwnedAccount(command.getAccountId(), command.getMemberId());
    Optional<Position> position = positionRepository.findByAccountIdAndSymbol(command.getAccountId(), command.getSymbol());
    return new AccountPositionSnapshot(account, command.getSymbol(), position);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AccountPositionsSnapshot getOwnedPositiveAccountPositions(AccountPositionsQueryCommand command) {
    Account account = getOwnedAccount(command.getAccountId(), command.getMemberId());
    List<Position> positions = positionRepository.findAllByAccountIdAndQtyGreaterThanOrderBySymbolAsc(
        command.getAccountId(),
        java.math.BigDecimal.ZERO
    );
    return new AccountPositionsSnapshot(account, positions);
  }

  @Transactional(readOnly = true)
  public AccountPositionsSnapshot getOwnedAccountPositions(AccountStatusQueryCommand command) {
    Account account = getOwnedAccount(command.getAccountId(), command.getMemberId());
    List<Position> positions = positionRepository.findAllByAccountIdOrderBySymbolAsc(command.getAccountId());
    return new AccountPositionsSnapshot(account, positions);
  }

  private Account getOwnedAccount(Long accountId, Long memberId) {
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "account not found"));

    if (!account.getMemberId().equals(memberId)) {
      throw new BusinessException(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP, "forbidden account ownership");
    }
    return account;
  }

  public record AccountPositionSnapshot(
      Account account,
      String symbol,
      Optional<Position> position
  ) {
  }

  public record AccountPositionsSnapshot(
      Account account,
      List<Position> positions
  ) {
  }
}
