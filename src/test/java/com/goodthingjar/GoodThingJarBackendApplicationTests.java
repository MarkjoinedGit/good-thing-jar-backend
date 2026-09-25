package com.goodthingjar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GoodThingJarBackendApplicationTests {
  @Test
  void applicationEntryPointUsesAuthoritativePackage() {
    assertThat(GoodThingJarBackendApplication.class.getPackageName()).isEqualTo("com.goodthingjar");
  }
}
