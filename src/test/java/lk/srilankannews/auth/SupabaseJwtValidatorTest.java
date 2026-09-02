package lk.srilankannews.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class SupabaseJwtValidatorTest {

    private final SupabaseAuthProperties properties = new SupabaseAuthProperties(
            "https://project.supabase.co/auth/v1", "https://project.supabase.co/jwks", "authenticated");
    private final OAuth2TokenValidator<Jwt> validator = new SecurityConfiguration().jwtValidator(properties);

    @Test
    void acceptsExpectedIssuerAudienceExpiryAndSubject() {
        assertThat(validator.validate(jwt(properties.issuer(), List.of("authenticated"), "user-1", 300)).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsWrongIssuerAudienceExpiredAndMissingSubject() {
        assertThat(validator.validate(jwt("https://evil.example", List.of("authenticated"), "user-1", 300)).hasErrors())
                .isTrue();
        assertThat(validator.validate(jwt(properties.issuer(), List.of("other"), "user-1", 300)).hasErrors())
                .isTrue();
        assertThat(validator.validate(jwt(properties.issuer(), List.of("authenticated"), "user-1", -300)).hasErrors())
                .isTrue();
        assertThat(validator.validate(jwt(properties.issuer(), List.of("authenticated"), "", 300)).hasErrors())
                .isTrue();
    }

    private Jwt jwt(String issuer, List<String> audience, String subject, long expiresInSeconds) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("safe-test-token")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issuedAt(expiresInSeconds < 0 ? now.minusSeconds(600) : now.minusSeconds(10))
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .build();
    }
}
