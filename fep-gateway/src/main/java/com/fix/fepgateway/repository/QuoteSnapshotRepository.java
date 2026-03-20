package com.fix.fepgateway.repository;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.entity.QuoteSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteSnapshotRepository extends JpaRepository<QuoteSnapshot, Long> {
  Optional<QuoteSnapshot> findByQuoteSnapshotId(String quoteSnapshotId);

  Optional<QuoteSnapshot> findTopBySymbolAndSourceModeOrderByQuoteAsOfDescStreamOffsetDesc(
      String symbol,
      FepQuoteSourceMode sourceMode
  );

  List<QuoteSnapshot> findBySymbolInAndSourceModeOrderBySymbolAscQuoteAsOfDescStreamOffsetDesc(
      List<String> symbols,
      FepQuoteSourceMode sourceMode
  );
}
