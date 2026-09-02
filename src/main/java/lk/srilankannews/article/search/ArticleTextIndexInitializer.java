package lk.srilankannews.article.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lk.srilankannews.article.Article;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ArticleTextIndexInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleTextIndexInitializer.class);
    public static final String INDEX_NAME = "idx_articles_public_text";
    public static final String DEFAULT_LANGUAGE = "none";
    public static final Map<String, Float> FIELD_WEIGHTS = fieldWeights();

    private final MongoOperations mongoOperations;

    public ArticleTextIndexInitializer(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexOperations = mongoOperations.indexOps(Article.class);
        List<IndexInfo> textIndexes = indexOperations.getIndexInfo().stream()
                .filter(info -> info.getIndexFields().stream().anyMatch(IndexField::isText))
                .toList();
        if (textIndexes.isEmpty()) {
            indexOperations.ensureIndex(definition());
            LOGGER.info("article_text_index_created index={}", INDEX_NAME);
            return;
        }
        if (textIndexes.size() != 1 || !compatible(textIndexes.get(0))) {
            LOGGER.error("article_text_index_incompatible expected={} existing={}",
                    INDEX_NAME, textIndexes.stream().map(IndexInfo::getName).toList());
            throw new IllegalStateException(
                    "Articles collection has an incompatible MongoDB text index; no index was changed. "
                            + "Review the existing text index and the documented manual recovery steps.");
        }
        LOGGER.info("article_text_index_verified index={}", INDEX_NAME);
    }

    static TextIndexDefinition definition() {
        TextIndexDefinition.TextIndexDefinitionBuilder builder = TextIndexDefinition.builder()
                .named(INDEX_NAME).withDefaultLanguage(DEFAULT_LANGUAGE);
        FIELD_WEIGHTS.forEach(builder::onField);
        return builder.build();
    }

    static boolean compatible(IndexInfo index) {
        Set<IndexField> expectedFields = FIELD_WEIGHTS.entrySet().stream()
                .map(entry -> IndexField.text(entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());

        Set<IndexField> actualTextFields = index.getIndexFields().stream()
                .filter(IndexField::isText)
                .filter(field -> !field.getKey().startsWith("_fts"))
                .collect(Collectors.toSet());

        boolean onlyTextAndMongoMetadata = index.getIndexFields().stream()
                .allMatch(field -> field.isText() || field.getKey().startsWith("_fts"));

        return INDEX_NAME.equals(index.getName())
                && DEFAULT_LANGUAGE.equals(index.getLanguage())
                && !index.isUnique()
                && !index.isSparse()
                && onlyTextAndMongoMetadata
                && actualTextFields.equals(expectedFields);
    }

    private static Map<String, Float> fieldWeights() {
        Map<String, Float> weights = new LinkedHashMap<>();
        weights.put("title", 10F);
        weights.put("aiEnrichment.summary", 4F);
        weights.put("aiEnrichment.topics", 6F);
        for (String language : List.of("EN", "SI", "TA")) {
            weights.put("translations." + language + ".title", 10F);
            weights.put("translations." + language + ".summary", 4F);
        }
        return Map.copyOf(weights);
    }
}
