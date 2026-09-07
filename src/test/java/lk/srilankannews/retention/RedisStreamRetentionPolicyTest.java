package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lk.srilankannews.retention.RedisStreamRetentionPolicy.GroupState;
import lk.srilankannews.retention.RedisStreamRetentionPolicy.PolicyResult;
import org.junit.jupiter.api.Test;

class RedisStreamRetentionPolicyTest {

    private final Instant now = Instant.ofEpochMilli(1000000L);
    private final Duration historyWindow = Duration.ofMillis(200000L); // age cutoff = 800000-0

    @Test
    void ageCutoffWinsWhenEarlierThanGroupBoundary() {
        GroupState group = new GroupState("group-1", "900000-0", 0, null);
        PolicyResult result = RedisStreamRetentionPolicy.calculateThreshold(
                "article-discovered", now, historyWindow, List.of(group), false, false);

        assertThat(result.safeToTrim()).isTrue();
        assertThat(result.ageCutoffId().rawId()).isEqualTo("800000-0");
        assertThat(result.protectionBoundary().rawId()).isEqualTo("900000-0");
        assertThat(result.finalThreshold().rawId()).isEqualTo("800000-0");
    }

    @Test
    void pendingBoundaryWinsWhenEarlierThanAgeCutoff() {
        GroupState group = new GroupState("group-1", "900000-0", 3, "750000-0");
        PolicyResult result = RedisStreamRetentionPolicy.calculateThreshold(
                "article-discovered", now, historyWindow, List.of(group), false, false);

        assertThat(result.safeToTrim()).isTrue();
        assertThat(result.ageCutoffId().rawId()).isEqualTo("800000-0");
        assertThat(result.protectionBoundary().rawId()).isEqualTo("750000-0");
        assertThat(result.finalThreshold().rawId()).isEqualTo("750000-0");
    }

    @Test
    void multipleGroupsEarliestBoundaryWins() {
        GroupState groupA = new GroupState("group-A", "900000-0", 0, null);
        GroupState groupB = new GroupState("group-B", "500000-0", 0, null);
        PolicyResult result = RedisStreamRetentionPolicy.calculateThreshold(
                "article-discovered", now, historyWindow, List.of(groupA, groupB), false, false);

        assertThat(result.safeToTrim()).isTrue();
        assertThat(result.protectionBoundary().rawId()).isEqualTo("500000-0");
        assertThat(result.finalThreshold().rawId()).isEqualTo("500000-0");
    }

    @Test
    void groupAtZeroZeroBlocksTrim() {
        GroupState group = new GroupState("group-1", "0-0", 0, null);
        PolicyResult result = RedisStreamRetentionPolicy.calculateThreshold(
                "article-discovered", now, historyWindow, List.of(group), false, false);

        assertThat(result.safeToTrim()).isFalse();
        assertThat(result.reason()).contains("GROUP BOUNDARY UNKNOWN OR AT 0-0");
    }

    @Test
    void inconsistentPendingStateBlocksTrim() {
        GroupState group = new GroupState("group-1", "900000-0", 0, null);
        PolicyResult result = RedisStreamRetentionPolicy.calculateThreshold(
                "article-discovered", now, historyWindow, List.of(group), true, false);

        assertThat(result.safeToTrim()).isFalse();
        assertThat(result.reason()).contains("PENDING ENTRY BODY MISSING / STREAM STATE INCONSISTENT");
    }

    @Test
    void noGroupsBlocksTrim() {
        PolicyResult result = RedisStreamRetentionPolicy.calculateThreshold(
                "article-discovered", now, historyWindow, List.of(), false, false);

        assertThat(result.safeToTrim()).isFalse();
        assertThat(result.reason()).isEqualTo("UNSAFE — NO CONSUMER GROUP");
    }

    @Test
    void dlqStreamRejection() {
        PolicyResult result = RedisStreamRetentionPolicy.calculateThreshold(
                "article-discovered-dlq", now, historyWindow, List.of(), false, true);

        assertThat(result.safeToTrim()).isFalse();
        assertThat(result.reason()).isEqualTo("UNSAFE — DLQ STREAM CANNOT BE AUTOMATICALLY TRIMMED");
    }
}
