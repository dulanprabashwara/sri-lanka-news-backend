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
        loadDotenvLocal();
        SpringApplication.run(SriLankaNewsApplication.class, args);
    }

    private static void loadDotenvLocal() {
        for (String filename : new String[]{".env.local", ".env"}) {
            for (java.nio.file.Path dir : new java.nio.file.Path[]{
                    java.nio.file.Paths.get("."),
                    java.nio.file.Paths.get(".."),
                    java.nio.file.Paths.get("sri-lanka-news-backend")
            }) {
                java.nio.file.Path file = dir.resolve(filename);
                if (java.nio.file.Files.exists(file)) {
                    try {
                        for (String line : java.nio.file.Files.readAllLines(file)) {
                            String trimmed = line.trim();
                            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                                continue;
                            }
                            int idx = trimmed.indexOf('=');
                            String key = trimmed.substring(0, idx).trim();
                            String value = trimmed.substring(idx + 1).trim();
                            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                                value = value.substring(1, value.length() - 1);
                            } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
