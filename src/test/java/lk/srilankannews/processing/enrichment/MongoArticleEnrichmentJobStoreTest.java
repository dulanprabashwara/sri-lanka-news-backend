package lk.srilankannews.processing.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class MongoArticleEnrichmentJobStoreTest {
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Mock
    private MongoOperations mongo;

    private MongoArticleEnrichmentJobStore store;

    @BeforeEach
    void setUp() {
        store = new MongoArticleEnrichmentJobStore(mongo);
    }

    @Test
    void ensurePendingInsertsNewJob() {
        store.ensurePending("article-1", NOW);

        ArgumentCaptor<ArticleEnrichmentJob> captor = ArgumentCaptor.forClass(ArticleEnrichmentJob.class);
        verify(mongo).insert(captor.capture());

        ArticleEnrichmentJob job = captor.getValue();
        assertThat(job.articleId()).isEqualTo("article-1");
        assertThat(job.status()).isEqualTo(EnrichmentStatus.PENDING);
        assertThat(job.attempts()).isEqualTo(0);
        assertThat(job.nextRetryAt()).isEqualTo(NOW);
    }

    @Test
    void ensurePendingIgnoresDuplicateKeyException() {
        when(mongo.insert(any(ArticleEnrichmentJob.class))).thenThrow(new DuplicateKeyException("exists"));

        store.ensurePending("article-1", NOW);
    }

    @Test
    void claimExecutesAtomicFindAndModify() {
        ArticleEnrichmentJob expected = new ArticleEnrichmentJob(
                "article-1", EnrichmentStatus.PROCESSING, 1, null,
                NOW.plus(Duration.ofMinutes(2)), "token-1", null, NOW);

        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(ArticleEnrichmentJob.class)))
                .thenReturn(expected);

        Optional<ArticleEnrichmentJob> result = store.claim("article-1", NOW, Duration.ofMinutes(2), 5);

        assertThat(result).contains(expected);
    }

    @Test
    void findDueArticleIdsQueriesMatchingJobs() {
        when(mongo.find(any(Query.class), eq(Document.class), any(String.class)))
                .thenReturn(List.of(new Document("_id", "article-1"), new Document("_id", "article-2")));

        List<String> dueIds = store.findDueArticleIds(NOW, 5, 10);

        assertThat(dueIds).containsExactly("article-1", "article-2");
    }

    @Test
    void findDueArticleIdsDoesNotInstantiateInvalidPartialArticleEnrichmentJob() {
        // Raw Mongo document containing ONLY _id projection, without attempts or status
        Document partialProjection = new Document("_id", "article-due-123");
        when(mongo.find(any(Query.class), eq(Document.class), any(String.class)))
                .thenReturn(List.of(partialProjection));

        List<String> dueIds = store.findDueArticleIds(NOW, 5, 10);

        assertThat(dueIds).containsExactly("article-due-123");
    }

    @Test
    void legacyOrIncompleteJobDocumentsHandledSafely() {
        // Document with missing attempts and missing status does not violate domain invariants
        ArticleEnrichmentJob legacyJob = new ArticleEnrichmentJob(
                "article-legacy", null, null, null, null, null, null, NOW);

        assertThat(legacyJob.articleId()).isEqualTo("article-legacy");
        assertThat(legacyJob.attempts()).isEqualTo(0);
        assertThat(legacyJob.status()).isEqualTo(EnrichmentStatus.PENDING);
    }

    @Test
    void markSucceededUpdatesStatusAndClearsLease() {
        store.markSucceeded("article-1", "token-1", NOW);

        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(ArticleEnrichmentJob.class));
    }

    @Test
    void markDeferredUpdatesStatusAndSetsNextRetry() {
        Instant retryAt = NOW.plus(Duration.ofMinutes(5));
        store.markDeferred("article-1", "token-1", "RATE_LIMIT", retryAt, NOW);

        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(ArticleEnrichmentJob.class));
    }

    @Test
    void markFailedUpdatesStatusToFailed() {
        store.markFailed("article-1", "token-1", "INVALID_REQUEST", NOW);

        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(ArticleEnrichmentJob.class));
    }

    @Test
    void markAlreadySucceededUpdatesStatusWithoutClaimCheck() {
        store.markAlreadySucceeded("article-1", NOW);

        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(ArticleEnrichmentJob.class));
    }
}
