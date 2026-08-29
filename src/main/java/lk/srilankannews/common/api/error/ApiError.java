package lk.srilankannews.common.api.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId,
        List<Detail> details
) {
    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public record Detail(String field, String code, String message) {
    }
}
