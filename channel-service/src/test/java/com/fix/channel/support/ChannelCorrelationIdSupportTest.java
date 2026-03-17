package com.fix.channel.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChannelCorrelationIdSupportTest {

  @Test
  void shouldNormalizeChannelCorrelationIdsToThirtySixCharacters() {
    String correlationId = "trace-channel-auth-very-long-correlation-id-000001";

    assertThat(ChannelCorrelationIdSupport.normalize(correlationId))
        .hasSize(ChannelCorrelationIdSupport.MAX_CHANNEL_CORRELATION_ID_LENGTH)
        .isEqualTo(correlationId.substring(0, ChannelCorrelationIdSupport.MAX_CHANNEL_CORRELATION_ID_LENGTH));
  }
}
