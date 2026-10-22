/*
 * File overview: Registers Spring components and runtime configuration for Time.
 * Area: cross-cutting application configuration.
 */
package lk.srilankannews.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
