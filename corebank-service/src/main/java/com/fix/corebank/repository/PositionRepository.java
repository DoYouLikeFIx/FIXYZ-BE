package com.fix.corebank.repository;

import com.fix.corebank.entity.Position;
import java.util.List;
import com.fix.corebank.repository.custom.PositionCustomRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long>, PositionCustomRepository {

  List<Position> findAllByAccountIdOrderBySymbolAsc(Long accountId);
}
