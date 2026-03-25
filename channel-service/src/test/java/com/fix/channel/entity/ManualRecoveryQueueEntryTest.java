package com.fix.channel.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ManualRecoveryQueueEntryTest {

  @Test
  void refreshShouldClearResolutionStateWhenResolvedEntryIsReEnqueued() {
    ManualRecoveryQueueEntry entry = ManualRecoveryQueueEntry.pending(
        "session-1",
        "clord-1",
        1,
        "ESCALATED_MANUAL_REVIEW",
        Instant.parse("2026-03-25T00:00:00Z")
    );
    entry.markPublished(Instant.parse("2026-03-25T00:01:00Z"));
    entry.markResolved(
        "operator-1",
        "COMPLETED",
        Instant.parse("2026-03-25T00:02:00Z")
    );

    entry.refresh(
        2,
        "ESCALATED_MANUAL_REVIEW",
        Instant.parse("2026-03-25T00:03:00Z")
    );

    assertThat(entry.getAttemptCount()).isEqualTo(2);
    assertThat(entry.getPublishedAt()).isNull();
    assertThat(entry.getResolvedBy()).isNull();
    assertThat(entry.getResolution()).isNull();
    assertThat(entry.getResolvedAt()).isNull();
    assertThat(entry.getPublishClaimToken()).isNull();
    assertThat(entry.getPublishClaimedAt()).isNull();
  }
}
