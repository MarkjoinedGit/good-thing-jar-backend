package com.goodthingjar.architecture;

import com.goodthingjar.GoodThingJarBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {
  @Test
  void modulesHaveNoCycles() {
    ApplicationModules.of(GoodThingJarBackendApplication.class).verify();
  }
}
