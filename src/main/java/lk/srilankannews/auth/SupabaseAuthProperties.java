/*
 * File overview: Binds and validates external configuration for Supabase Auth.
 * Area: authentication and authorization.
 */
package lk.srilankannews.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("supabase.auth")
public record SupabaseAuthProperties(String issuer, String jwksUri, String audience) {
}
