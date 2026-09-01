package lk.srilankannews.ai;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

public final class GeminiFailureMapper {
    private GeminiFailureMapper() {
    }

    public static AiProviderException map(RuntimeException exception, String model) {
        if (exception instanceof ApiException apiException) {
            return mapApi(apiException, model);
        }
        AiProviderException.Kind kind =
                exception instanceof GenAiIOException || containsTimeout(exception)
                        ? AiProviderException.Kind.TIMEOUT_NETWORK
                        : AiProviderException.Kind.PROVIDER_FAILURE;
        String code = kind == AiProviderException.Kind.TIMEOUT_NETWORK
                ? "NETWORK_OR_TIMEOUT"
                : "SDK_FAILURE";
        return new AiProviderException(
                kind,
                "Gemini request failed",
                null,
                code,
                kind == AiProviderException.Kind.TIMEOUT_NETWORK
                        ? "Gemini network request failed"
                        : "Gemini SDK request failed",
                model,
                exception);
    }

    private static AiProviderException mapApi(ApiException exception, String model) {
        int statusCode = exception.code();
        String providerCode = exception.status();
        String providerMessage = exception.message();
        AiProviderException.Kind kind =
                classify(statusCode, providerCode, providerMessage);
        return new AiProviderException(
                kind,
                "Gemini request failed",
                statusCode,
                providerCode,
                providerMessage,
                model,
                exception);
    }

    static AiProviderException.Kind classify(
            int statusCode, String providerCode, String providerMessage) {
        String detail = ((providerCode == null ? "" : providerCode) + " "
                + (providerMessage == null ? "" : providerMessage)).toUpperCase(Locale.ROOT);
        if (statusCode == 429 || detail.contains("RESOURCE_EXHAUSTED")
                || detail.contains("QUOTA") || detail.contains("RATE LIMIT")) {
            return AiProviderException.Kind.RATE_LIMIT;
        }
        if (statusCode == 401 || detail.contains("UNAUTHENTICATED")
                || detail.contains("API KEY NOT VALID") || detail.contains("INVALID API KEY")) {
            return AiProviderException.Kind.AUTHENTICATION;
        }
        if (statusCode == 403 || detail.contains("PERMISSION_DENIED")) {
            return AiProviderException.Kind.PERMISSION;
        }
        if (statusCode == 404 || detail.contains("MODEL") && detail.contains("NOT FOUND")) {
            return AiProviderException.Kind.MODEL_NOT_FOUND;
        }
        if (statusCode == 400 || statusCode == 422 || detail.contains("INVALID_ARGUMENT")) {
            return AiProviderException.Kind.INVALID_REQUEST;
        }
        if (statusCode >= 500) {
            return AiProviderException.Kind.PROVIDER_5XX;
        }
        return AiProviderException.Kind.PROVIDER_FAILURE;
    }

    private static boolean containsTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }
}
