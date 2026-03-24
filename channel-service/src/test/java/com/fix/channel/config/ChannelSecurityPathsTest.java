package com.fix.channel.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChannelSecurityPathsTest {

  @Test
  void prometheusEndpointIsPublicForObservabilityScrapes() {
    assertThat(ChannelSecurityPaths.isPublicPath("/actuator/prometheus")).isTrue();
    assertThat(ChannelSecurityPaths.requiresAdminRole("/actuator/prometheus")).isFalse();
    assertThat(ChannelSecurityPaths.requiresAuthentication("/actuator/prometheus")).isFalse();
  }
}
