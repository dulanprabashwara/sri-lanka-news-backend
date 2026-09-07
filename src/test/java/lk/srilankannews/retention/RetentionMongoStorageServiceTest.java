package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"unchecked", "rawtypes"})
class RetentionMongoStorageServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private com.mongodb.client.MongoDatabase mongoDatabase;
    @Mock
    private com.mongodb.client.MongoCollection<Document> mongoCollection;
    @Mock
    private com.mongodb.client.ListIndexesIterable<Document> listIndexesIterable;

    private RetentionMongoStorageService service;

    @BeforeEach
    void setUp() {
        service = new RetentionMongoStorageService(mongoTemplate);
    }

    @Test
    void getStorageOverviewReturnsDbStatsAndCollectionMetrics() {
        Document dbStats = new Document()
                .append("dataSize", 1000L)
                .append("storageSize", 2000L)
                .append("indexSize", 500L)
                .append("collections", 10L);

        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        when(mongoDatabase.runCommand(any(Document.class))).thenAnswer(invocation -> {
            Document cmd = invocation.getArgument(0);
            if (cmd.containsKey("dbStats")) {
                return dbStats;
            }
            return new Document("count", 100L).append("size", 200L).append("totalIndexSize", 50L);
        });
        when(mongoTemplate.collectionExists("articles")).thenReturn(true);

        RetentionMongoStorageService.MongoStorageOverview overview = service.getStorageOverview();

        assertThat(overview.available()).isTrue();
        assertThat(overview.dataSize()).isEqualTo(1000L);
        assertThat(overview.storageSize()).isEqualTo(2000L);
        assertThat(overview.collections()).isNotEmpty();
    }

    @Test
    void getTtlIndexHealthIdentifiesHealthyIndexes() {
        when(mongoTemplate.collectionExists(any(String.class))).thenReturn(true);
        when(mongoTemplate.getCollection(any(String.class))).thenReturn(mongoCollection);
        when(mongoCollection.listIndexes()).thenReturn(listIndexesIterable);

        Document ttlIndex = new Document("key", new Document("expiresAt", 1))
                .append("expireAfterSeconds", 0L);

        when(listIndexesIterable.iterator()).thenAnswer(invocation -> {
            com.mongodb.client.MongoCursor cursor = mock(com.mongodb.client.MongoCursor.class);
            when(cursor.hasNext()).thenReturn(true, false);
            when(cursor.next()).thenReturn(ttlIndex);
            return cursor;
        });

        List<RetentionMongoStorageService.TtlIndexHealthStatus> healthList = service.getTtlIndexHealth();

        assertThat(healthList).isNotEmpty();
        RetentionMongoStorageService.TtlIndexHealthStatus notificationsHealth = healthList.stream()
                .filter(h -> h.collectionName().equals("notifications"))
                .findFirst()
                .orElseThrow();
        assertThat(notificationsHealth.status()).isEqualTo("HEALTHY");
    }

    @Test
    void getTtlDocumentHealthReportsLifecycleCountsAndClassifications() {
        Instant now = Instant.parse("2026-09-07T12:00:00Z");
        when(mongoTemplate.collectionExists("notifications")).thenReturn(true);
        when(mongoTemplate.count(any(Query.class), eq("notifications"))).thenReturn(100L, 80L, 20L, 5L);

        Document earliestDoc = new Document("expiresAt", Date.from(now.plusSeconds(3600)));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("notifications"))).thenReturn(earliestDoc);

        List<RetentionMongoStorageService.TtlDocumentLifecycleStatus> docHealth = service.getTtlDocumentHealth(now);

        RetentionMongoStorageService.TtlDocumentLifecycleStatus notificationsStatus = docHealth.stream()
                .filter(d -> d.collectionName().equals("notifications"))
                .findFirst()
                .orElseThrow();

        assertThat(notificationsStatus.totalDocuments()).isEqualTo(100L);
        assertThat(notificationsStatus.withExpiresAt()).isEqualTo(80L);
        assertThat(notificationsStatus.withoutExpiresAt()).isEqualTo(20L);
        assertThat(notificationsStatus.expiredAwaitingCleanup()).isEqualTo(5L);
        assertThat(notificationsStatus.missingExpiryClassification()).contains("ACTIVE PROTECTED");
    }
}
