package lk.srilankannews.analytics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
public class AnalyticsPseudonymService {

    private final byte[] secretKey;
    private final Clock clock;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    public AnalyticsPseudonymService(
            @Value("${analytics.pseudonym.secret:}") String secret,
            Clock clock) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String generateVisitorKey(String subject) {
        if (secretKey.length == 0 || subject == null || subject.isBlank()) {
            return null; // Degraded state if not configured or empty subject
        }
        try {
            String date = DATE_FORMATTER.format(clock.instant());
            String payload = date + "|" + subject;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            return null;
        }
    }
}
