package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lk.srilankannews.article.Article;
import lk.srilankannews.ingestion.run.IngestionRunStatus;
import lk.srilankannews.ingestion.settings.IngestionSourceSettings;
import lk.srilankannews.ingestion.trigger.IngestionTriggerRequest;
import lk.srilankannews.notifications.NotificationEvent.EventStatus;
import lk.srilankannews.notifications.NotificationPreference;
import lk.srilankannews.source.Source;
import lk.srilankannews.story.Story;
import lk.srilankannews.user.UserBookmark;
import lk.srilankannews.user.UserFollow;
import lk.srilankannews.user.UserPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetentionPolicyServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private RetentionPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new RetentionPolicyService(RetentionProperties.defaults());
    }

    @Test
    void notificationReadExpiryCalculatedCorrectly() {
        Optional<Instant> expiry = policyService.calculateNotificationReadExpiry(BASE_TIME);
        assertThat(expiry).contains(BASE_TIME.plus(180, ChronoUnit.DAYS));
    }

    @Test
    void notificationUnreadMaxExpiryCalculatedCorrectly() {
        Optional<Instant> expiry = policyService.calculateNotificationUnreadMaxExpiry(BASE_TIME);
        assertThat(expiry).contains(BASE_TIME.plus(365, ChronoUnit.DAYS));
    }

    @Test
    void notificationOutboxExpiryByStatus() {
        Optional<Instant> published = policyService.calculateNotificationOutboxExpiry(EventStatus.PUBLISHED, BASE_TIME);
        assertThat(published).contains(BASE_TIME.plus(30, ChronoUnit.DAYS));

        Optional<Instant> failed = policyService.calculateNotificationOutboxExpiry(EventStatus.FAILED, BASE_TIME);
        assertThat(failed).contains(BASE_TIME.plus(90, ChronoUnit.DAYS));

        Optional<Instant> pending = policyService.calculateNotificationOutboxExpiry(EventStatus.PENDING, BASE_TIME);
        assertThat(pending).isEmpty();

        Optional<Instant> retrying = policyService.calculateNotificationOutboxExpiry(EventStatus.RETRYING, BASE_TIME);
        assertThat(retrying).isEmpty();
    }

    @Test
    void ingestionRunExpiryByStatus() {
        Optional<Instant> completed = policyService.calculateIngestionRunExpiry(IngestionRunStatus.COMPLETED, BASE_TIME);
        assertThat(completed).contains(BASE_TIME.plus(90, ChronoUnit.DAYS));

        Optional<Instant> failed = policyService.calculateIngestionRunExpiry(IngestionRunStatus.FAILED, BASE_TIME);
        assertThat(failed).contains(BASE_TIME.plus(90, ChronoUnit.DAYS));

        Optional<Instant> interrupted = policyService.calculateIngestionRunExpiry(IngestionRunStatus.INTERRUPTED, BASE_TIME);
        assertThat(interrupted).contains(BASE_TIME.plus(90, ChronoUnit.DAYS));

        Optional<Instant> running = policyService.calculateIngestionRunExpiry(IngestionRunStatus.RUNNING, BASE_TIME);
        assertThat(running).isEmpty();
    }

    @Test
    void ingestionTriggerExpiryByStatus() {
        Optional<Instant> completed = policyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_COMPLETED, BASE_TIME);
        assertThat(completed).contains(BASE_TIME.plus(30, ChronoUnit.DAYS));

        Optional<Instant> failed = policyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_FAILED, BASE_TIME);
        assertThat(failed).contains(BASE_TIME.plus(30, ChronoUnit.DAYS));

        Optional<Instant> cancelled = policyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_CANCELLED, BASE_TIME);
        assertThat(cancelled).contains(BASE_TIME.plus(30, ChronoUnit.DAYS));

        Optional<Instant> pending = policyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_PENDING, BASE_TIME);
        assertThat(pending).isEmpty();

        Optional<Instant> claimed = policyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_CLAIMED, BASE_TIME);
        assertThat(claimed).isEmpty();
    }

    @Test
    void adminAuditExpiryCalculatedCorrectly() {
        Optional<Instant> expiry = policyService.calculateAdminAuditExpiry(BASE_TIME);
        assertThat(expiry).contains(BASE_TIME.plus(365, ChronoUnit.DAYS));
    }

    @Test
    void nullTimestampsReturnEmptyOptional() {
        assertThat(policyService.calculateNotificationReadExpiry(null)).isEmpty();
        assertThat(policyService.calculateNotificationUnreadMaxExpiry(null)).isEmpty();
        assertThat(policyService.calculateNotificationOutboxExpiry(EventStatus.PUBLISHED, null)).isEmpty();
        assertThat(policyService.calculateIngestionRunExpiry(IngestionRunStatus.COMPLETED, null)).isEmpty();
        assertThat(policyService.calculateIngestionTriggerExpiry(IngestionTriggerRequest.STATUS_COMPLETED, null)).isEmpty();
        assertThat(policyService.calculateAdminAuditExpiry(null)).isEmpty();
    }

    @Test
    void neverExpireEntitiesRegistryContract() {
        assertThat(NeverExpireEntities.isNeverExpireClass(Article.class)).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireClass(Story.class)).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireClass(Source.class)).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireClass(UserPreferences.class)).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireClass(NotificationPreference.class)).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireClass(UserBookmark.class)).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireClass(UserFollow.class)).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireClass(IngestionSourceSettings.class)).isTrue();

        assertThat(NeverExpireEntities.isNeverExpireCollection("articles")).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireCollection("stories")).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireCollection("sources")).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireCollection("user_preferences")).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireCollection("notification_preferences")).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireCollection("user_bookmarks")).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireCollection("user_follows")).isTrue();
        assertThat(NeverExpireEntities.isNeverExpireCollection("ingestion_source_settings")).isTrue();

        // Verify transient / temporary collections are NOT registered as never-expire
        assertThat(NeverExpireEntities.isNeverExpireCollection("notifications")).isFalse();
        assertThat(NeverExpireEntities.isNeverExpireCollection("ingestion_runs")).isFalse();
        assertThat(NeverExpireEntities.isNeverExpireCollection("analytics_events")).isFalse();
    }
}
