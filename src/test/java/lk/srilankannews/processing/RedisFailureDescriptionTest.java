package lk.srilankannews.processing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisFailureDescriptionTest {

    @Test
    void reportsDeepestCauseAndRedactsRedisCredentials() {
        RuntimeException exception = new RuntimeException(
                "Redis system failure",
                new IllegalStateException(
                        "Unable to connect to rediss://default:super-secret@redis.example.com:6379"));

        RedisFailureDescription.Details details = RedisFailureDescription.from(exception);

        assertThat(details.rootCause()).isEqualTo("IllegalStateException");
        assertThat(details.message())
                .contains("rediss://***@redis.example.com:6379")
                .doesNotContain("super-secret");
    }

    @Test
    void findsErrorCodeAnywhereInCauseChain() {
        RuntimeException exception = new RuntimeException(
                "RedisSystemException",
                new IllegalStateException("BUSYGROUP Consumer Group name already exists"));

        assertThat(RedisFailureDescription.containsCode(exception, "BUSYGROUP")).isTrue();
    }
}
