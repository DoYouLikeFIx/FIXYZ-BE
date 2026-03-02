package com.fix.common;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CoreCommonPurityTest {

  @Test
  void coreCommonHasNoSpringDependency() {
    assertThrows(ClassNotFoundException.class, () -> Class.forName("org.springframework.context.ApplicationContext"));
  }
}
