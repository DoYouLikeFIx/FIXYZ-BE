package com.fix.fepgateway.dataplane.marketdata.replay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReplaySequenceHasherTest {

  private final ReplaySequenceHasher replaySequenceHasher = new ReplaySequenceHasher();

  @Test
  void shouldProduceStableHashForIdenticalSequence() {
    List<String> entries = List.of(
        "qsnap_alpha",
        "qsnap_beta",
        "qsnap_gamma"
    );

    String first = replaySequenceHasher.hashSequence(entries);
    String second = replaySequenceHasher.hashSequence(entries);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldChangeHashWhenSequenceOrderChanges() {
    String ordered = replaySequenceHasher.hashSequence(List.of("qsnap_alpha", "qsnap_beta"));
    String reordered = replaySequenceHasher.hashSequence(List.of("qsnap_beta", "qsnap_alpha"));

    assertThat(ordered).isNotEqualTo(reordered);
  }

  @Test
  void shouldUseStableBaselineForEmptySequence() {
    assertThat(replaySequenceHasher.hashSequence(List.of()))
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }
}
