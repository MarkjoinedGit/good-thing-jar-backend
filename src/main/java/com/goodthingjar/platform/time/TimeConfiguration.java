package com.goodthingjar.platform.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
  @Bean
  Clock trustedClock() {
    return Clock.systemUTC();
  }
}
