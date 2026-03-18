package com.fix.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrelationIdSupportTest {

  @Test
  void shouldNormalizeBlankCorrelationIdToNull() {
    assertThat(CorrelationIdSupport.normalize(null)).isNull();
    assertThat(CorrelationIdSupport.normalize(" ")).isNull();
  }

  @Test
  void shouldPreservePlatformCorrelationIdsUpToSixtyFourCharacters() {
    String correlationId = "trace-channel-auth-very-long-correlation-id-000001";

    assertThat(CorrelationIdSupport.normalize(correlationId)).isEqualTo(correlationId);
  }

  @Test
  void shouldAllowExplicitCanonicalLengthNormalization() {
    String correlationId = "trace-channel-auth-very-long-correlation-id-000001";

    assertThat(CorrelationIdSupport.normalize(correlationId, 36)).hasSize(36);
    assertThat(CorrelationIdSupport.normalize(correlationId, 36)).isEqualTo(correlationId.substring(0, 36));
  }
}
