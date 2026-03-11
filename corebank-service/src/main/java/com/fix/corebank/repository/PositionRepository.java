package com.fix.corebank.repository;

import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.custom.PositionCustomRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long>, PositionCustomRepository {
  List<Position> findAllByAccountIdAndQtyGreaterThanOrderBySymbolAsc(Long accountId, BigDecimal minimumQty);
}
