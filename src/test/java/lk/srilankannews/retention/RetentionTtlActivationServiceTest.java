package lk.srilankannews.retention;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
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
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetentionTtlActivationServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoCollection<Document> mongoCollection;

    @Mock
    private ListIndexesIterable<Document> listIndexesIterable;

    @Mock
    private MongoCursor<Document> mongoCursor;

    @Mock
    private IndexOperations indexOperations;

    @BeforeEach
    void setUp() {
        lenient().when(mongoTemplate.getCollection(anyString())).thenReturn(mongoCollection);
        lenient().when(mongoCollection.listIndexes()).thenReturn(listIndexesIterable);
        lenient().when(listIndexesIterable.into(any())).thenAnswer(invocation -> {
            List<Document> list = invocation.getArgument(0);
            return list;
        });
        lenient().when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);
        lenient().when(mongoTemplate.count(any(Query.class), anyString())).thenAnswer(inv -> {
            Query query = inv.getArgument(0);
            Document queryObject = query.getQueryObject();
            if (queryObject.containsKey("expiresAt")) {
                Object expObj = queryObject.get("expiresAt");
                if (expObj instanceof Document doc && doc.containsKey("$lte")) {
                    return 0L; // 0 immediate expired records by default
                }
            }
            return 10L;
        });
    }

    @Test
    void testActivationDisabled_returnsEmptyList() {
        RetentionTtlActivationProperties props = new RetentionTtlActivationProperties(false, false, 100);
        RetentionTtlActivationService service = new RetentionTtlActivationService(mongoTemplate, props);

        List<RetentionTtlActivationResult> results = service.performActivation();
        assertTrue(results.isEmpty());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void testPreviewMode_returnsSkippedResults() {
        RetentionTtlActivationProperties props = new RetentionTtlActivationProperties(true, false, 100);
        RetentionTtlActivationService service = new RetentionTtlActivationService(mongoTemplate, props);

        List<RetentionTtlActivationResult> results = service.performActivation();
        assertEquals(5, results.size());
        for (RetentionTtlActivationResult res : results) {
            assertEquals(RetentionTtlActivationResult.Status.SKIPPED, res.status());
            assertEquals(0, res.preImmediateExpiredCount());
        }
        verify(indexOperations, never()).ensureIndex(any());
    }

    @Test
    void testApplyMode_createsIndexesSuccessfully() {
        RetentionTtlActivationProperties props = new RetentionTtlActivationProperties(true, true, 100);
        RetentionTtlActivationService service = new RetentionTtlActivationService(mongoTemplate, props);

        List<RetentionTtlActivationResult> results = service.performActivation();
        assertEquals(5, results.size());
        for (RetentionTtlActivationResult res : results) {
            assertEquals(RetentionTtlActivationResult.Status.CREATED, res.status());
            assertEquals(0, res.preImmediateExpiredCount());
        }
        verify(indexOperations, times(5)).ensureIndex(any());
    }

    @Test
    void testSafetyGateViolation_throwsException() {
        RetentionTtlActivationProperties props = new RetentionTtlActivationProperties(true, true, 100);
        RetentionTtlActivationService service = new RetentionTtlActivationService(mongoTemplate, props);

        when(mongoTemplate.count(any(Query.class), eq("admin_audit_events"))).thenAnswer(inv -> {
            Query query = inv.getArgument(0);
            Document queryObject = query.getQueryObject();
            if (queryObject.containsKey("expiresAt")) {
                Object expObj = queryObject.get("expiresAt");
                if (expObj instanceof Document doc && doc.containsKey("$lte")) {
                    return 2L; // 2 immediate expired records -> trigger safety gate
                }
            }
            return 10L;
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::performActivation);
        assertTrue(ex.getMessage().contains("SAFETY GATE VIOLATION"));
    }

    @Test
    void testIdempotency_matchingExistingTtlIndex_skipsCreation() {
        RetentionTtlActivationProperties props = new RetentionTtlActivationProperties(true, true, 100);
        RetentionTtlActivationService service = new RetentionTtlActivationService(mongoTemplate, props);

        Document existingIdx = new Document("name", "ttl_admin_audit_events_expiresAt")
                .append("key", new Document("expiresAt", 1))
                .append("expireAfterSeconds", 0L);

        when(listIndexesIterable.into(any())).thenAnswer(invocation -> {
            List<Document> list = invocation.getArgument(0);
            list.add(existingIdx);
            return list;
        });

        List<RetentionTtlActivationResult> results = service.performActivation();
        assertEquals(5, results.size());
        assertEquals(RetentionTtlActivationResult.Status.EXISTING_MATCH, results.get(0).status());
    }

    @Test
    void testConflictDetection_nonTtlIndex_throwsException() {
        RetentionTtlActivationProperties props = new RetentionTtlActivationProperties(true, true, 100);
        RetentionTtlActivationService service = new RetentionTtlActivationService(mongoTemplate, props);

        Document nonTtlIdx = new Document("name", "ttl_admin_audit_events_expiresAt")
                .append("key", new Document("expiresAt", 1));

        when(listIndexesIterable.into(any())).thenAnswer(invocation -> {
            List<Document> list = invocation.getArgument(0);
            list.add(nonTtlIdx);
            return list;
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::performActivation);
        assertTrue(ex.getMessage().contains("CONFLICT DETECTED"));
    }
}
