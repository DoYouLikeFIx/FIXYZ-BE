package com.fix.fepgateway.dataplane.marketdata.replay;

import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import com.fix.fepgateway.entity.ReplayCursor;
import com.fix.fepgateway.repository.ReplayCursorRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReplayCursorPersistenceService implements ReplayCursorPersistencePort {

  private static final String RUNNING = "RUNNING";
  private static final String PAUSED = "PAUSED";
  private static final String STOPPED = "STOPPED";

  private final ReplayCursorRepository replayCursorRepository;

  @Override
  @Transactional
  public ReplayCursorSpec activate(ReplayCursorSpec replayCursorSpec) {
    ReplayCursor replayCursor = replayCursorRepository.findByReplayId(replayCursorSpec.replayId())
        .orElseGet(() -> ReplayCursor.running(
            replayCursorSpec.replayId(),
            replayCursorSpec.seed(),
            replayCursorSpec.symbol(),
            replayCursorSpec.speedFactor()
        ));

    replayCursor.synchronize(
        replayCursorSpec.seed(),
        replayCursorSpec.symbol(),
        replayCursorSpec.speedFactor()
    );
    if (replayCursor.getCursorOffset() == null || replayCursor.getCursorOffset() < replayCursorSpec.cursorOffset()) {
      replayCursor.moveTo(replayCursorSpec.cursorOffset());
    }
    replayCursor.changeStatus(RUNNING);

    return toSpec(replayCursorRepository.save(replayCursor));
  }

  @Override
  @Transactional
  public ReplayCursorSpec reset(ReplayCursorSpec replayCursorSpec) {
    ReplayCursor replayCursor = replayCursorRepository.findByReplayId(replayCursorSpec.replayId())
        .orElseGet(() -> ReplayCursor.running(
            replayCursorSpec.replayId(),
            replayCursorSpec.seed(),
            replayCursorSpec.symbol(),
            replayCursorSpec.speedFactor()
        ));

    replayCursor.synchronize(
        replayCursorSpec.seed(),
        replayCursorSpec.symbol(),
        replayCursorSpec.speedFactor()
    );
    replayCursor.moveTo(replayCursorSpec.cursorOffset());
    replayCursor.changeStatus(RUNNING);

    return toSpec(replayCursorRepository.save(replayCursor));
  }

  @Override
  @Transactional
  public ReplayCursorSpec advance(String replayId, long nextCursorOffset) {
    ReplayCursor replayCursor = replayCursorRepository.findByReplayId(replayId)
        .orElseThrow(() -> new IllegalStateException("Replay cursor not found: " + replayId));
    replayCursor.moveTo(nextCursorOffset);
    replayCursor.changeStatus(RUNNING);
    return toSpec(replayCursorRepository.save(replayCursor));
  }

  @Override
  @Transactional
  public void pause(String replayId) {
    replayCursorRepository.findByReplayId(replayId).ifPresent(replayCursor -> {
      replayCursor.changeStatus(PAUSED);
      replayCursorRepository.save(replayCursor);
    });
  }

  @Override
  @Transactional
  public void resume(String replayId) {
    replayCursorRepository.findByReplayId(replayId).ifPresent(replayCursor -> {
      replayCursor.changeStatus(RUNNING);
      replayCursorRepository.save(replayCursor);
    });
  }

  @Override
  @Transactional
  public void stop(String replayId) {
    replayCursorRepository.findByReplayId(replayId).ifPresent(replayCursor -> {
      replayCursor.changeStatus(STOPPED);
      replayCursorRepository.save(replayCursor);
    });
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReplayCursorSpec> find(String replayId) {
    return replayCursorRepository.findByReplayId(replayId).map(this::toSpec);
  }

  private ReplayCursorSpec toSpec(ReplayCursor replayCursor) {
    return new ReplayCursorSpec(
        replayCursor.getReplayId(),
        replayCursor.getSeed(),
        replayCursor.getSymbol(),
        replayCursor.getCursorOffset(),
        replayCursor.getSpeedFactor()
    );
  }
}
