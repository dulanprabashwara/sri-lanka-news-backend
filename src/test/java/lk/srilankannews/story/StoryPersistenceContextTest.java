package lk.srilankannews.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.processing.ArticleDiscoveredNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.transaction.support.TransactionOperations;

class StoryPersistenceContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ArticleRepository.class, () -> mock(ArticleRepository.class))
            .withBean(StoryRepository.class, () -> mock(StoryRepository.class))
            .withBean(MongoOperations.class, () -> mock(MongoOperations.class))
            .withBean(StoryMatcher.class, () -> mock(StoryMatcher.class))
            .withBean(StoryClusteringProperties.class,
                    () -> mock(StoryClusteringProperties.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ArticleDiscoveredNotifier.class,
                    () -> mock(ArticleDiscoveredNotifier.class))
            .withBean(
                    "storyClusteringTransactionOperations",
                    TransactionOperations.class,
                    () -> mock(TransactionOperations.class))
            .withUserConfiguration(Phase12StoryBeanScanConfiguration.class);

    @Test
    void createsCompletePhase12ProductionBeanGraph() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StoryPersistence.class);
            assertThat(context).hasSingleBean(ArticleStoryAssignment.class);
            assertThat(context).hasSingleBean(StoryClusterPartitionStore.class);
            assertThat(context).hasSingleBean(StoryClusterPartitionInitializer.class);
            assertThat(context).hasSingleBean(StoryClusteringBackfill.class);
            assertThat(context).hasSingleBean(StoryClusteringService.class);
            assertThat(context).hasSingleBean(StoryClusteringTransactionExecutor.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = StoryPersistence.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            StoryPersistence.class,
                            ArticleStoryAssignment.class,
                            StoryClusterPartitionStore.class,
                            StoryClusterPartitionInitializer.class,
                            StoryClusteringBackfill.class,
                            StoryClusteringService.class,
                            StoryClusteringTransactionExecutor.class
                    }))
    static class Phase12StoryBeanScanConfiguration {
    }
}
