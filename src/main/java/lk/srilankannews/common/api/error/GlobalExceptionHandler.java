package lk.srilankannews.common.api.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import lk.srilankannews.config.InvalidIngestionApiKeyException;
import lk.srilankannews.config.RequestCorrelationFilter;
import lk.srilankannews.ai.AiProviderException;
import lk.srilankannews.article.search.InvalidSearchQueryException;
import lk.srilankannews.article.search.SemanticSearchUnavailableException;
import lk.srilankannews.article.search.SemanticSearchWindowException;
import lk.srilankannews.story.ask.AskStoryUnavailableException;
import lk.srilankannews.story.ask.InvalidAskStoryQuestionException;
import lk.srilankannews.admin.AdminRetryNotAllowedException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<ApiError.Detail> details = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new ApiError.Detail(error.getField(), error.getCode(), safeMessage(error)))
                .toList();

        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Request validation failed.", servletRequest(request), details);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<ApiError.Detail> details = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiError.Detail(
                                parameterName(result.getMethodParameter().getParameterName()),
                                constraintCode(error),
                                safeMessage(error))))
                .toList();

        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Request validation failed.", servletRequest(request), details);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        log.error("HttpMessageNotReadableException: ", exception);
        return response(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Request body is malformed or unreadable.", servletRequest(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found.", servletRequest(request), List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ApiError.Detail> details = exception.getConstraintViolations().stream()
                .map(violation -> new ApiError.Detail(
                        violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()))
                .sorted(Comparator.comparing(ApiError.Detail::field))
                .toList();

        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Request validation failed.", request, details);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String field = exception instanceof MethodArgumentTypeMismatchException methodArgumentException
                ? methodArgumentException.getName()
                : "parameter";
        List<ApiError.Detail> details = List.of(
                new ApiError.Detail(field, "TypeMismatch", "Invalid value."));

        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Request validation failed.", servletRequest(request), details);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Object> handleResourceNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidIngestionApiKeyException.class)
    ResponseEntity<Object> handleInvalidIngestionApiKey(
            InvalidIngestionApiKeyException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidSearchQueryException.class)
    ResponseEntity<Object> handleInvalidSearchQuery(
            InvalidSearchQueryException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                exception.getMessage(), request, List.of(
                        new ApiError.Detail("q", "InvalidSearchQuery", exception.getMessage())));
    }

    @ExceptionHandler(SemanticSearchWindowException.class)
    ResponseEntity<Object> handleSemanticSearchWindow(
            SemanticSearchWindowException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                exception.getMessage(), request, List.of(
                        new ApiError.Detail("page", "SemanticSearchWindow", exception.getMessage())));
    }

    @ExceptionHandler(SemanticSearchUnavailableException.class)
    ResponseEntity<Object> handleSemanticSearchUnavailable(
            SemanticSearchUnavailableException exception,
            HttpServletRequest request
    ) {
        log.warn("Semantic search unavailable cause={}",
                exception.getCause() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getCause().getClass().getSimpleName());
        return response(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SEMANTIC_SEARCH_UNAVAILABLE,
                "Semantic search is temporarily unavailable.", request, List.of());
    }

    @ExceptionHandler(InvalidAskStoryQuestionException.class)
    ResponseEntity<Object> handleInvalidAskStoryQuestion(
            InvalidAskStoryQuestionException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                exception.getMessage(), request, List.of(
                        new ApiError.Detail("question", "InvalidQuestion", exception.getMessage())));
    }

    @ExceptionHandler(AskStoryUnavailableException.class)
    ResponseEntity<Object> handleAskStoryUnavailable(
            AskStoryUnavailableException exception,
            HttpServletRequest request
    ) {
        logAskStoryUnavailable(exception, request);
        return response(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.ASK_STORY_UNAVAILABLE,
                "Ask This Story is temporarily unavailable.", request, List.of());
    }

    private void logAskStoryUnavailable(
            AskStoryUnavailableException exception,
            HttpServletRequest request
    ) {
        String requestId = resolveRequestId(request);
        Throwable cause = exception.getCause();

        StringBuilder message = new StringBuilder("Ask This Story unavailable");
        if (cause instanceof AiProviderException aiException) {
            message.append(" cause=").append(aiException.getClass().getSimpleName());
            if (aiException.provider() != null && !aiException.provider().isBlank()) {
                message.append(" provider=").append(aiException.provider());
            }
            if (aiException.kind() != null) {
                message.append(" category=").append(aiException.kind().name());
            }
            if (aiException.httpStatus() != null) {
                message.append(" status=").append(aiException.httpStatus());
            }
            String safeError = resolveSafeErrorMessage(aiException);
            if (safeError != null && !safeError.isBlank()) {
                message.append(" error=\"").append(safeError.replace('"', '\'')).append("\"");
            }
        } else {
            String causeName = cause == null
                    ? exception.getClass().getSimpleName()
                    : cause.getClass().getSimpleName();
            message.append(" cause=").append(causeName);
            String rawError = cause != null && cause.getMessage() != null
                    ? cause.getMessage()
                    : exception.getMessage();
            String safeError = AiProviderException.sanitize(rawError);
            if (safeError != null && !safeError.isBlank()) {
                message.append(" error=\"").append(safeError.replace('"', '\'')).append("\"");
            }
        }

        if (requestId != null && !requestId.isBlank()) {
            message.append(" requestId=").append(requestId);
        }

        log.warn("{}", message);
    }

    private String resolveSafeErrorMessage(AiProviderException exception) {
        if (exception.providerMessage() != null && !exception.providerMessage().isBlank()) {
            return exception.providerMessage();
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return AiProviderException.sanitize(exception.getMessage());
        }
        return null;
    }

    private String resolveRequestId(HttpServletRequest request) {
        String mdcId = MDC.get("requestId");
        if (mdcId != null && !mdcId.isBlank()) {
            return mdcId;
        }
        if (request != null) {
            String headerId = request.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
            if (headerId != null && !headerId.isBlank()) {
                return headerId;
            }
        }
        return null;
    }

    @ExceptionHandler(AdminRetryNotAllowedException.class)
    ResponseEntity<Object> handleAdminRetryNotAllowed(
            AdminRetryNotAllowedException exception,
            HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "Admin access is required.", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), exception);

        return response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.", request, List.of());
    }

    private ResponseEntity<Object> response(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            List<ApiError.Detail> details
    ) {
        ApiError apiError = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code.name(),
                message,
                request.getRequestURI(),
                MDC.get("requestId"),
                details);

        return ResponseEntity.status(status).body(apiError);
    }

    private String safeMessage(MessageSourceResolvable error) {
        return error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage();
    }

    private String constraintCode(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        return codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
    }

    private String parameterName(String name) {
        return name == null || name.isBlank() ? "parameter" : name;
    }

    private HttpServletRequest servletRequest(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }

    private enum ErrorCode {
        UNAUTHORIZED,
        VALIDATION_ERROR,
        MALFORMED_REQUEST,
        RESOURCE_NOT_FOUND,
        SEMANTIC_SEARCH_UNAVAILABLE,
        ASK_STORY_UNAVAILABLE,
        CONFLICT,
        FORBIDDEN,
        INTERNAL_ERROR
    }
}
