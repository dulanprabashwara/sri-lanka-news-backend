package lk.srilankannews.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            URI uri = new URI(value);
            return uri.isAbsolute()
                    && ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
