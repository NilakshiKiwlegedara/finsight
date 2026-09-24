package com.finsight_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class ReportingConfig {
    @Bean
    public Clock reportingClock() {
        return Clock.systemDefaultZone();
    }
}
