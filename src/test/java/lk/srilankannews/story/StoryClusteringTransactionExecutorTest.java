package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.MongoException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class StoryClusteringTransactionExecutorTest {

    @Test
    void transientWriteConflictRetriesTheCompleteTransaction() {
        AtomicInteger transactionCalls = new AtomicInteger();
        AtomicInteger workCalls = new AtomicInteger();
        TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                T result = action.doInTransaction((TransactionStatus) null);
                if (transactionCalls.incrementAndGet() == 1) {
                    throw new MongoException(112, "WriteConflict");
                }
                return result;
            }
        };
        StoryClusteringTransactionExecutor executor =
                new StoryClusteringTransactionExecutor(transactions);

        assertThat(executor.execute(() -> {
            workCalls.incrementAndGet();
            return "story-1";
        })).isEqualTo("story-1");

        assertThat(transactionCalls).hasValue(2);
        assertThat(workCalls).hasValue(2);
    }

    @Test
    void exhaustedTransientRetriesPropagateTheMongoFailure() {
        AtomicInteger workCalls = new AtomicInteger();
        TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                action.doInTransaction((TransactionStatus) null);
                throw new MongoException(112, "WriteConflict");
            }
        };
        StoryClusteringTransactionExecutor executor =
                new StoryClusteringTransactionExecutor(transactions);

        assertThatThrownBy(() -> executor.execute(() -> {
            workCalls.incrementAndGet();
            return "story-1";
        })).isInstanceOf(MongoException.class)
                .hasMessageContaining("WriteConflict");

        assertThat(workCalls).hasValue(StoryClusteringTransactionExecutor.MAX_ATTEMPTS);
    }

    @Test
    void nonTransientMongoFailureIsNotRetried() {
        AtomicInteger workCalls = new AtomicInteger();
        TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                action.doInTransaction((TransactionStatus) null);
                throw new MongoException(13, "Unauthorized");
            }
        };
        StoryClusteringTransactionExecutor executor =
                new StoryClusteringTransactionExecutor(transactions);

        assertThatThrownBy(() -> executor.execute(() -> {
            workCalls.incrementAndGet();
            return "story-1";
        })).isInstanceOf(MongoException.class);

        assertThat(workCalls).hasValue(1);
    }
}
