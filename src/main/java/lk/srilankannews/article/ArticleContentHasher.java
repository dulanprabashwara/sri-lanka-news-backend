package lk.srilankannews.article;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

@Component
public class ArticleContentHasher {

    public String hash(String content) {
        String normalized = normalize(content);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    String normalize(String content) {
        String unicodeNormalized = Normalizer.normalize(content, Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(unicodeNormalized.length());
        boolean pendingSpace = false;

        for (int offset = 0; offset < unicodeNormalized.length();) {
            int codePoint = unicodeNormalized.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.appendCodePoint(codePoint);
            }
        }

        return result.toString();
    }
}
