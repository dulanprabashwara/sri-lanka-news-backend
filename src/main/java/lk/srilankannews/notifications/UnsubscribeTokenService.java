package lk.srilankannews.notifications;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UnsubscribeTokenService {

    private final byte[] secretBytes;
    private final Clock clock;
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String PURPOSE_VALUE = "notification-email-unsubscribe";
    private static final long EXPIRATION_MILLIS = 30L * 24 * 60 * 60 * 1000; // 30 days

    public UnsubscribeTokenService(
            @Value("${notification.unsubscribe.secret:fallback-secret-key-that-should-be-at-least-256-bits-long}") String secret,
            Clock clock) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String generateToken(String userId) {
        try {
            Instant now = clock.instant();
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .claim(PURPOSE_CLAIM, PURPOSE_VALUE)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusMillis(EXPIRATION_MILLIS)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(new MACSigner(secretBytes));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate unsubscribe token", e);
        }
    }

    public String validateTokenAndGetUserId(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new MACVerifier(secretBytes))) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime() != null && claims.getExpirationTime().before(Date.from(clock.instant()))) {
                throw new IllegalArgumentException("Expired token");
            }

            if (!PURPOSE_VALUE.equals(claims.getStringClaim(PURPOSE_CLAIM))) {
                throw new IllegalArgumentException("Invalid token purpose");
            }
            return claims.getSubject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired token", e);
        }
    }
}
