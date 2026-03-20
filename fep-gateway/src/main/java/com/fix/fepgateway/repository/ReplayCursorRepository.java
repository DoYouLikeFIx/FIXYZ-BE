package com.fix.fepgateway.repository;

import com.fix.fepgateway.entity.ReplayCursor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplayCursorRepository extends JpaRepository<ReplayCursor, Long> {
  Optional<ReplayCursor> findByReplayId(String replayId);

  List<ReplayCursor> findAllBySymbolAndStatusOrderByUpdatedAtDesc(String symbol, String status);
}
