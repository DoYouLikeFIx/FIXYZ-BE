package com.fix.fepgateway.dataplane.marketdata.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import com.fix.fepgateway.entity.ReplayCursor;
import com.fix.fepgateway.repository.ReplayCursorRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ReplayCursorPersistenceServiceTest {

  @Autowired
  private ReplayCursorPersistencePort replayCursorPersistencePort;

  @Autowired
  private ReplayCursorRepository replayCursorRepository;

  @BeforeEach
  void setUp() {
    replayCursorRepository.deleteAllInBatch();
  }

  @Test
  void shouldActivateCursorAndResumeStoredOffset() {
    ReplayCursorSpec initial = new ReplayCursorSpec(
        "replay-005930",
        "seed-1",
        "005930",
        0L,
        new BigDecimal("1.0000")
    );

    replayCursorPersistencePort.activate(initial);
    replayCursorPersistencePort.advance("replay-005930", 5L);

    ReplayCursorSpec resumed = replayCursorPersistencePort.activate(initial);

    assertThat(resumed.cursorOffset()).isEqualTo(5L);
  }

  @Test
  void shouldStopCursorWithoutDroppingCurrentOffset() {
    ReplayCursorSpec initial = new ReplayCursorSpec(
        "replay-005930",
        "seed-1",
        "005930",
        3L,
        new BigDecimal("1.0000")
    );

    replayCursorPersistencePort.activate(initial);
    replayCursorPersistencePort.stop("replay-005930");

    ReplayCursor replayCursor = replayCursorRepository.findByReplayId("replay-005930").orElseThrow();
    assertThat(replayCursor.getCursorOffset()).isEqualTo(3L);
    assertThat(replayCursor.getStatus()).isEqualTo("STOPPED");
  }

  @Test
  void shouldResetCursorToRequestedOffsetWhenTimelineRestarts() {
    ReplayCursorSpec initial = new ReplayCursorSpec(
        "replay-005930",
        "seed-1",
        "005930",
        0L,
        new BigDecimal("1.0000")
    );

    replayCursorPersistencePort.activate(initial);
    replayCursorPersistencePort.advance("replay-005930", 7L);

    ReplayCursorSpec reset = replayCursorPersistencePort.reset(new ReplayCursorSpec(
        "replay-005930",
        "seed-1",
        "005930",
        2L,
        new BigDecimal("1.5000")
    ));

    assertThat(reset.cursorOffset()).isEqualTo(2L);
    ReplayCursor replayCursor = replayCursorRepository.findByReplayId("replay-005930").orElseThrow();
    assertThat(replayCursor.getCursorOffset()).isEqualTo(2L);
    assertThat(replayCursor.getSpeedFactor()).isEqualByComparingTo("1.5000");
    assertThat(replayCursor.getStatus()).isEqualTo("RUNNING");
  }

  @Test
  void shouldPauseAndResumeCursorStatusWithoutChangingOffset() {
    ReplayCursorSpec initial = new ReplayCursorSpec(
        "replay-005930",
        "seed-1",
        "005930",
        4L,
        new BigDecimal("1.0000")
    );

    replayCursorPersistencePort.activate(initial);
    replayCursorPersistencePort.pause("replay-005930");

    ReplayCursor paused = replayCursorRepository.findByReplayId("replay-005930").orElseThrow();
    assertThat(paused.getStatus()).isEqualTo("PAUSED");
    assertThat(paused.getCursorOffset()).isEqualTo(4L);

    replayCursorPersistencePort.resume("replay-005930");

    ReplayCursor resumed = replayCursorRepository.findByReplayId("replay-005930").orElseThrow();
    assertThat(resumed.getStatus()).isEqualTo("RUNNING");
    assertThat(resumed.getCursorOffset()).isEqualTo(4L);
  }
}
