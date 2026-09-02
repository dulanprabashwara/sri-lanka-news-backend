package lk.srilankannews.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({SupabaseAuthProperties.class, AdminProperties.class})
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/admin", "/api/v1/admin/**").authenticated()
                        .requestMatchers("/api/v1/me", "/api/v1/me/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(
                        (request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), Map.of(
                                    "timestamp", Instant.now().toString(),
                                    "status", 403,
                                    "error", "Forbidden",
                                    "code", "FORBIDDEN",
                                    "message", "Admin access is required.",
                                    "path", request.getRequestURI(),
                                    "details", List.of()));
                        }))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), Map.of(
                                    "timestamp", Instant.now().toString(),
                                    "status", 401,
                                    "error", "Unauthorized",
                                    "code", "UNAUTHORIZED",
                                    "message", "Authentication is required or the bearer token is invalid.",
                                    "path", request.getRequestURI(),
                                    "details", List.of()));
                        }))
                .build();
    }

    @Bean
    NimbusJwtDecoder jwtDecoder(SupabaseAuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(SignatureAlgorithm.ES256);
                    algorithms.add(SignatureAlgorithm.RS256);
                })
                .build();
        decoder.setJwtValidator(jwtValidator(properties));
        return decoder;
    }

    OAuth2TokenValidator<Jwt> jwtValidator(SupabaseAuthProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()),
                jwt -> jwt.getSubject() == null || jwt.getSubject().isBlank()
                        ? OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "JWT subject is required.", null))
                        : OAuth2TokenValidatorResult.success());
    }
}
