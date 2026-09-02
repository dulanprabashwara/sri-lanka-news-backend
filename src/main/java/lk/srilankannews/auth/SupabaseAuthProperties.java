package lk.srilankannews.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("supabase.auth")
public record SupabaseAuthProperties(String issuer, String jwksUri, String audience) {
}
