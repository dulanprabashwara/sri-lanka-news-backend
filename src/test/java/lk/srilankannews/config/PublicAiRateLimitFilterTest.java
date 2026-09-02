package lk.srilankannews.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PublicAiRateLimitFilterTest {

    private static final int SEMANTIC_LIMIT = 3;
    private static final int ASK_LIMIT = 2;
    private static final int MAX_CLIENTS = 100;

    private PublicAiRateLimitFilter filter;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        filter = new PublicAiRateLimitFilter(
                new PublicAiRateLimitProperties(SEMANTIC_LIMIT, ASK_LIMIT, MAX_CLIENTS),
                mapper,
                clock);
    }

    // ── Normal requests pass through ──────────────────────────────

    @Test
    void allowsSemanticSearchWithinLimit() throws Exception {
        for (int i = 0; i < SEMANTIC_LIMIT; i++) {
            MockHttpServletResponse response = doSemanticSearch("10.0.0.1");
            assertThat(response.getStatus()).as("request %d", i + 1).isEqualTo(200);
        }
    }

    @Test
    void allowsAskStoryWithinLimit() throws Exception {
        for (int i = 0; i < ASK_LIMIT; i++) {
            MockHttpServletResponse response = doAskStory("10.0.0.1");
            assertThat(response.getStatus()).as("request %d", i + 1).isEqualTo(200);
        }
    }

    // ── Threshold exceeded → 429 ──────────────────────────────────

    @Test
    void rejectsSemanticSearchWhenLimitExceeded() throws Exception {
        for (int i = 0; i < SEMANTIC_LIMIT; i++) {
            doSemanticSearch("10.0.0.2");
        }
        MockHttpServletResponse response = doSemanticSearch("10.0.0.2");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        int retryAfter = Integer.parseInt(response.getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1, 60);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void rejectsAskStoryWhenLimitExceeded() throws Exception {
        for (int i = 0; i < ASK_LIMIT; i++) {
            doAskStory("10.0.0.3");
        }
        MockHttpServletResponse response = doAskStory("10.0.0.3");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
    }

    // ── Semantic and Ask limits are independent ───────────────────

    @Test
    void semanticAndAskLimitsAreIndependent() throws Exception {
        String client = "10.0.0.4";
        // exhaust semantic limit
        for (int i = 0; i < SEMANTIC_LIMIT; i++) {
            doSemanticSearch(client);
        }
        assertThat(doSemanticSearch(client).getStatus()).isEqualTo(429);
        // ask-story should still be allowed
        assertThat(doAskStory(client).getStatus()).isEqualTo(200);
    }

    // ── Ordinary public browsing is not limited ──────────────────

    @Test
    void ordinaryBrowsingEndpointsAreNotRateLimited() throws Exception {
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
            request.setRemoteAddr("10.0.0.5");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void getStoriesEndpointIsNotRateLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stories");
            request.setRemoteAddr("10.0.0.5");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void textSearchEndpointIsNotRateLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
            request.setRemoteAddr("10.0.0.5");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    // ── Uses remoteAddr, NOT X-Forwarded-For ─────────────────────

    @Test
    void usesRemoteAddrNotForwardedFor() throws Exception {
        String spoofedIp = "192.168.0.100";
        String realIp = "10.0.0.6";

        // Exhaust the limit from realIp
        for (int i = 0; i < SEMANTIC_LIMIT; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search/semantic");
            request.setRemoteAddr(realIp);
            request.addHeader("X-Forwarded-For", spoofedIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // Next request from same remoteAddr with different X-Forwarded-For should be rejected
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search/semantic");
        request.setRemoteAddr(realIp);
        request.addHeader("X-Forwarded-For", "99.99.99.99"); // different spoofed IP
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);
    }

    // ── Client IP is hashed, not persisted raw ───────────────────

    @Test
    void clientIdentityIsHashedNotRawIp() throws Exception {
        doSemanticSearch("10.0.0.7");
        MockHttpServletResponse rejectedResponse = null;
        for (int i = 0; i < SEMANTIC_LIMIT; i++) {
            rejectedResponse = doSemanticSearch("10.0.0.7");
        }
        // The 429 body should not contain the raw IP
        assertThat(rejectedResponse).isNotNull();
        assertThat(rejectedResponse.getContentAsString()).doesNotContain("10.0.0.7");
    }

    // ── Different clients have independent limits ────────────────

    @Test
    void differentClientsHaveIndependentLimits() throws Exception {
        // Exhaust limit for client A
        for (int i = 0; i < SEMANTIC_LIMIT; i++) {
            doSemanticSearch("10.0.0.8");
        }
        assertThat(doSemanticSearch("10.0.0.8").getStatus()).isEqualTo(429);

        // Client B should be fine
        assertThat(doSemanticSearch("10.0.0.9").getStatus()).isEqualTo(200);
    }

    // ── Memory is bounded by maxClients ──────────────────────────

    @Test
    void memoryIsBoundedByMaxClients() throws Exception {
        // Create a filter with very small maxClients
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PublicAiRateLimitFilter smallFilter = new PublicAiRateLimitFilter(
                new PublicAiRateLimitProperties(100, 100, 3),
                mapper, clock);

        // Fill up 3 client slots
        for (int clientNum = 0; clientNum < 3; clientNum++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search/semantic");
            request.setRemoteAddr("10.0.1." + clientNum);
            MockHttpServletResponse response = new MockHttpServletResponse();
            smallFilter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).as("client %d", clientNum).isEqualTo(200);
        }

        // A 4th client in the same window should be rejected (eviction only removes expired)
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search/semantic");
        request.setRemoteAddr("10.0.1.99");
        MockHttpServletResponse response = new MockHttpServletResponse();
        smallFilter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);
    }

    // ── Endpoint matching ────────────────────────────────────────

    @Test
    void matchesSemanticSearchEndpointExactly() throws Exception {
        // Exact match
        assertThat(doSemanticSearch("10.0.0.10").getStatus()).isEqualTo(200);
        // Not a prefix match — /api/v1/search/semantic/extra should pass through
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search/semantic/extra");
        request.setRemoteAddr("10.0.0.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        for (int i = 0; i < 100; i++) {
            filter.doFilter(request, response, new MockFilterChain());
        }
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void matchesAskStoryEndpointWithValidObjectId() throws Exception {
        // Valid 24-char hex ObjectId
        assertThat(doAskStory("10.0.0.11").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotMatchAskStoryWithInvalidObjectId() throws Exception {
        // Invalid ObjectId (not 24 hex chars)
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/stories/invalid/ask");
            request.setRemoteAddr("10.0.0.12");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private MockHttpServletResponse doSemanticSearch(String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search/semantic");
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse doAskStory(String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/stories/507f1f77bcf86cd799439011/ask");
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
