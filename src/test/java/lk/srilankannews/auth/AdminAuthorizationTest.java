package lk.srilankannews.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AdminAuthorizationTest {

    @Test
    void emptyAllowlistGrantsNobodyAccess() {
        var authorization = new AdminAuthorization(new AdminProperties(""));
        var authentication = new JwtAuthenticationToken(jwt("user-a"));

        assertThat(authorization.isAdmin(authentication)).isFalse();
    }

    @Test
    void onlyVerifiedSubjectControlsAuthorizationNotEmail() {
        var authorization = new AdminAuthorization(new AdminProperties("admin-sub"));

        assertThat(authorization.isAdmin(new JwtAuthenticationToken(jwt("admin-sub")))).isTrue();
        assertThat(authorization.isAdmin(new JwtAuthenticationToken(
                Jwt.withTokenValue("token").header("alg", "none").subject("reader-sub")
                        .claim("email", "admin-sub").build()))).isFalse();
    }

    private Jwt jwt(String subject) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
    }
}
