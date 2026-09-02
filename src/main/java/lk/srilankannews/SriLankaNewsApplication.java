package lk.srilankannews;

import lk.srilankannews.auth.SecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(SecurityConfiguration.class)
public class SriLankaNewsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SriLankaNewsApplication.class, args);
    }
}
