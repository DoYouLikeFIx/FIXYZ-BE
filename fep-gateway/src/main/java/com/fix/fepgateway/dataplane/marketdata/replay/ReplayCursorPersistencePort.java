package com.fix.fepgateway.dataplane.marketdata.replay;

import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import java.util.Optional;

public interface ReplayCursorPersistencePort {

  ReplayCursorSpec activate(ReplayCursorSpec replayCursorSpec);

  ReplayCursorSpec reset(ReplayCursorSpec replayCursorSpec);

  ReplayCursorSpec advance(String replayId, long nextCursorOffset);

  void pause(String replayId);

  void resume(String replayId);

  void stop(String replayId);

  Optional<ReplayCursorSpec> find(String replayId);
}
