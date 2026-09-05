package lk.srilankannews;

import lk.srilankannews.auth.SecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

@SpringBootApplication
@EnableConfigurationProperties(lk.srilankannews.config.PublicAiRateLimitProperties.class)
@Import(SecurityConfiguration.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class SriLankaNewsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SriLankaNewsApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
