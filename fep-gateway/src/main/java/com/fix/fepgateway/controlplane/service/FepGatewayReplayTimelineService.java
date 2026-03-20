package com.fix.fepgateway.controlplane.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import com.fix.fepgateway.dataplane.marketdata.replay.ReplayMarketDataAdapter;
import com.fix.fepgateway.dataplane.marketdata.replay.ReplayTimelineStatus;
import com.fix.fepgateway.entity.ReplayCursor;
import com.fix.fepgateway.repository.ReplayCursorRepository;
import com.fix.fepgateway.vo.GatewayReplayTimelineResult;
import com.fix.fepgateway.vo.GatewayReplayTimelineStartCommand;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FepGatewayReplayTimelineService {

  private final ReplayMarketDataAdapter replayMarketDataAdapter;
  private final ReplayCursorRepository replayCursorRepository;

  @Transactional
  public GatewayReplayTimelineResult startTimeline(GatewayReplayTimelineStartCommand command) {
    ReplayTimelineStatus status = replayMarketDataAdapter.startTimeline(new ReplayCursorSpec(
        replayIdFor(command.seed(), command.symbol()),
        command.seed(),
        command.symbol(),
        command.startOffset(),
        command.speedFactor()
    ));
    return toResult(status);
  }

  @Transactional(readOnly = true)
  public GatewayReplayTimelineResult getTimeline(String replayId) {
    ReplayTimelineStatus runtimeStatus = replayMarketDataAdapter.getTimelineStatus(replayId);
    if (runtimeStatus != null) {
      return toResult(runtimeStatus);
    }

    ReplayCursor replayCursor = replayCursorRepository.findByReplayId(replayId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "replay timeline not found"));

    return new GatewayReplayTimelineResult(
        replayCursor.getReplayId(),
        replayCursor.getSymbol(),
        replayCursor.getSeed(),
        replayCursor.getCursorOffset(),
        replayCursor.getSpeedFactor(),
        replayCursor.getStatus(),
        null,
        null
    );
  }

  private GatewayReplayTimelineResult toResult(ReplayTimelineStatus status) {
    return new GatewayReplayTimelineResult(
        status.replayId(),
        status.symbol(),
        status.seed(),
        status.cursorOffset(),
        status.speedFactor(),
        status.status(),
        status.emittedCount(),
        status.sequenceHash()
    );
  }

  private String replayIdFor(String seed, String symbol) {
    return UUID.nameUUIDFromBytes((seed + "|" + symbol).getBytes(StandardCharsets.UTF_8)).toString();
  }
}
