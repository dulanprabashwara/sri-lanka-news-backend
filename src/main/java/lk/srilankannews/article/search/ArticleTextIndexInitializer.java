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
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ArticleTextIndexInitializer implements ApplicationRunner {
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
            return;
        }
        if (textIndexes.size() != 1 || !compatible(textIndexes.get(0))) {
            throw new IllegalStateException(
                    "Articles collection has an incompatible MongoDB text index; no index was changed.");
        }
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
        return INDEX_NAME.equals(index.getName())
                && DEFAULT_LANGUAGE.equals(index.getLanguage())
                && Set.copyOf(index.getIndexFields()).equals(expectedFields);
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
