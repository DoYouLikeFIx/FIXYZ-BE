package com.fix.fepgateway.controlplane.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.fepgateway.repository.QuoteSnapshotRepository;
import com.fix.fepgateway.vo.GatewayQuoteSnapshotQueryCommand;
import com.fix.fepgateway.vo.GatewayQuoteSnapshotResult;
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
}
