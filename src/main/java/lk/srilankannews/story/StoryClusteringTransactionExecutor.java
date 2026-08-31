package lk.srilankannews.story;

import com.mongodb.MongoException;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

@Component
class StoryClusteringTransactionExecutor {
    static final int MAX_ATTEMPTS = 3;
    private static final int WRITE_CONFLICT_CODE = 112;
    private static final String TRANSIENT_TRANSACTION_ERROR = "TransientTransactionError";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(StoryClusteringTransactionExecutor.class);

    private final TransactionOperations transactions;

    StoryClusteringTransactionExecutor(
            @Qualifier("storyClusteringTransactionOperations")
            TransactionOperations transactions) {
        this.transactions = transactions;
    }

    String execute(Supplier<String> work) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return Objects.requireNonNull(
                        transactions.execute(status -> work.get()),
                        "Story clustering transaction returned no result");
            } catch (RuntimeException exception) {
                if (!isRetryable(exception) || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
                LOGGER.warn("story_cluster_transaction_retry attempt={} reason={}",
                        attempt, exception.getClass().getSimpleName());
            }
        }
        throw new IllegalStateException("Story clustering transaction retry loop exhausted");
    }

    private boolean isRetryable(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof MongoException mongoException
                    && (mongoException.hasErrorLabel(TRANSIENT_TRANSACTION_ERROR)
                    || mongoException.getCode() == WRITE_CONFLICT_CODE)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
