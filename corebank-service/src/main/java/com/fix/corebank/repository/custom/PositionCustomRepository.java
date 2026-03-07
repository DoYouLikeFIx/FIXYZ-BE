package com.fix.corebank.repository.custom;

import com.fix.corebank.entity.Position;
import java.util.Optional;

public interface PositionCustomRepository {
  Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);

  Optional<Position> findByAccountIdAndSymbolForUpdate(Long accountId, String symbol);
}
