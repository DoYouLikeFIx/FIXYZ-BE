package com.fix.fepgateway.repository;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.entity.QuoteSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteSnapshotRepository extends JpaRepository<QuoteSnapshot, Long> {
  Optional<QuoteSnapshot> findByQuoteSnapshotId(String quoteSnapshotId);

  Optional<QuoteSnapshot> findTopBySymbolAndSourceModeOrderByQuoteAsOfDesc(
      String symbol,
      FepQuoteSourceMode sourceMode
  );
}
