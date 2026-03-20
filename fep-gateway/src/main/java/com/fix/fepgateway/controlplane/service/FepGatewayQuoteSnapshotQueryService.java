package com.fix.fepgateway.controlplane.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.fepgateway.repository.QuoteSnapshotRepository;
import com.fix.fepgateway.vo.GatewayQuoteSnapshotBatchQueryCommand;
import com.fix.fepgateway.vo.GatewayQuoteSnapshotQueryCommand;
import com.fix.fepgateway.vo.GatewayQuoteSnapshotResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FepGatewayQuoteSnapshotQueryService {

  private final QuoteSnapshotRepository quoteSnapshotRepository;

  @Transactional(readOnly = true)
  public GatewayQuoteSnapshotResult getLatestSnapshot(GatewayQuoteSnapshotQueryCommand command) {
    return quoteSnapshotRepository
        .findTopBySymbolAndSourceModeOrderByQuoteAsOfDescStreamOffsetDesc(
            command.symbol(),
            command.quoteSourceMode()
        )
        .map(GatewayQuoteSnapshotResult::from)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "quote snapshot not found"));
  }

  @Transactional(readOnly = true)
  public List<GatewayQuoteSnapshotResult> getLatestSnapshots(GatewayQuoteSnapshotBatchQueryCommand command) {
    LinkedHashSet<String> requestedSymbols = new LinkedHashSet<>(command.symbols());
    if (requestedSymbols.isEmpty()) {
      return List.of();
    }

    Map<String, GatewayQuoteSnapshotResult> latestBySymbol = new LinkedHashMap<>();
    quoteSnapshotRepository
        .findBySymbolInAndSourceModeOrderBySymbolAscQuoteAsOfDescStreamOffsetDesc(
            new ArrayList<>(requestedSymbols),
            command.quoteSourceMode()
        )
        .forEach(snapshot -> latestBySymbol.putIfAbsent(snapshot.getSymbol(), GatewayQuoteSnapshotResult.from(snapshot)));

    return requestedSymbols.stream()
        .map(latestBySymbol::get)
        .filter(Objects::nonNull)
        .toList();
  }
}
