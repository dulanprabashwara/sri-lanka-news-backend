package lk.srilankannews.story;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class StoryMongoTransactionConfiguration {

    @Bean
    @ConditionalOnMissingBean(MongoTransactionManager.class)
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean("storyClusteringTransactionOperations")
    TransactionOperations storyClusteringTransactionOperations(
            MongoTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
