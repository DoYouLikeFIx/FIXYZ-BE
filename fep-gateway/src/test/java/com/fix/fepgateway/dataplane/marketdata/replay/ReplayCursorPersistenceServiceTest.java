package com.fix.fepgateway.dataplane.marketdata.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import com.fix.fepgateway.entity.ReplayCursor;
import com.fix.fepgateway.repository.ReplayCursorRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
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

  @AfterEach
  void cleanUp() {
    replayCursorRepository.deleteAll();
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
}
