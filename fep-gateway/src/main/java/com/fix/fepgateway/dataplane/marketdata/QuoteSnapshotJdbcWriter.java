package com.fix.fepgateway.dataplane.marketdata;

import com.fix.fepgateway.entity.QuoteSnapshot;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuoteSnapshotJdbcWriter {

  private static final String INSERT_SQL = """
      INSERT INTO fep_quote_snapshots (
          quote_snapshot_id,
          symbol,
          source_mode,
          quote_as_of,
          best_bid,
          best_ask,
          last_trade,
          stream_offset,
          is_stale,
          created_at,
          updated_at
      ) VALUES (
          :quoteSnapshotId,
          :symbol,
          :sourceMode,
          :quoteAsOf,
          :bestBid,
          :bestAsk,
          :lastTrade,
          :streamOffset,
          :stale,
          :createdAt,
          :updatedAt
      )
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public boolean insertIfAbsent(QuoteSnapshot snapshot) {
    Instant now = Instant.now();
    MapSqlParameterSource parameters = new MapSqlParameterSource()
        .addValue("quoteSnapshotId", snapshot.getQuoteSnapshotId())
        .addValue("symbol", snapshot.getSymbol())
        .addValue("sourceMode", snapshot.getSourceMode().name())
        .addValue("quoteAsOf", snapshot.getQuoteAsOf())
        .addValue("bestBid", snapshot.getBestBid())
        .addValue("bestAsk", snapshot.getBestAsk())
        .addValue("lastTrade", snapshot.getLastTrade())
        .addValue("streamOffset", snapshot.getStreamOffset())
        .addValue("stale", snapshot.isStale())
        .addValue("createdAt", now)
        .addValue("updatedAt", now);

    try {
      return jdbcTemplate.update(INSERT_SQL, parameters) > 0;
    } catch (DuplicateKeyException ignored) {
      return false;
    }
  }
}
