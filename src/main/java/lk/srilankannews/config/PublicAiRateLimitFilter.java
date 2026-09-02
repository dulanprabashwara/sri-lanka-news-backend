package lk.srilankannews.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PublicAiRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_SECONDS = 60;
    private static final long CLEANUP_INTERVAL = 256;

    private final PublicAiRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentHashMap<ClientEndpoint, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();

    public PublicAiRateLimitFilter(
            PublicAiRateLimitProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Endpoint endpoint = endpoint(request);
        if (endpoint == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = clock.instant().getEpochSecond();
        long windowStart = now - Math.floorMod(now, WINDOW_SECONDS);
        if (requests.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            removeExpired(windowStart);
        }
        ClientEndpoint key = new ClientEndpoint(clientHash(request.getRemoteAddr()), endpoint);
        if (!windows.containsKey(key) && windows.size() >= properties.maxClients()) {
            removeExpired(windowStart);
        }
        if (!windows.containsKey(key) && windows.size() >= properties.maxClients()) {
            reject(request, response, WINDOW_SECONDS - Math.floorMod(now, WINDOW_SECONDS));
            return;
        }

        int limit = endpoint == Endpoint.SEMANTIC_SEARCH
                ? properties.semanticSearchPerMinute()
                : properties.askStoryPerMinute();
        Window result = windows.compute(key, (ignored, existing) ->
                existing == null || existing.startedAt() != windowStart
                        ? new Window(windowStart, 1)
                        : new Window(windowStart, existing.count() + 1));
        if (result.count() > limit) {
            reject(request, response, WINDOW_SECONDS - Math.floorMod(now, WINDOW_SECONDS));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Endpoint endpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("GET".equals(request.getMethod()) && "/api/v1/search/semantic".equals(path)) {
            return Endpoint.SEMANTIC_SEARCH;
        }
        if ("POST".equals(request.getMethod())
                && path.matches("/api/v1/stories/[0-9a-fA-F]{24}/ask")) {
            return Endpoint.ASK_STORY;
        }
        return null;
    }

    private void removeExpired(long currentWindowStart) {
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt() < currentWindowStart);
    }

    private String clientHash(String remoteAddress) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (remoteAddress == null ? "unknown" : remoteAddress)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfter)
            throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(Math.max(1, retryAfter)));
        objectMapper.writeValue(response.getOutputStream(), new RateLimitError(
                Instant.now(clock), 429, "Too Many Requests", "RATE_LIMITED",
                "Too many AI-powered requests. Please try again shortly.",
                request.getRequestURI(), MDC.get("requestId"), List.of()));
    }

    private enum Endpoint { SEMANTIC_SEARCH, ASK_STORY }

    private record ClientEndpoint(String clientHash, Endpoint endpoint) { }

    private record Window(long startedAt, int count) { }

    private record RateLimitError(
            Instant timestamp, int status, String error, String code, String message,
            String path, String requestId, List<Object> details) { }
}
