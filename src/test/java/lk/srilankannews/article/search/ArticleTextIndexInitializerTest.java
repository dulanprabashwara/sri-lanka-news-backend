package lk.srilankannews.article.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import lk.srilankannews.article.Article;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ExtendWith(MockitoExtension.class)
class ArticleTextIndexInitializerTest {
    @Mock MongoOperations mongoOperations;
    @Mock IndexOperations indexOperations;

    @Test
    void definesExactlyTheWeightedPublicSafeMultilingualFields() {
        IndexDefinition definition = ArticleTextIndexInitializer.definition();
        Document options = definition.getIndexOptions();
        Document weights = options.get("weights", Document.class);

        assertThat(definition.getIndexKeys()).containsOnlyKeys(
                "title", "aiEnrichment.summary", "aiEnrichment.topics",
                "translations.EN.title", "translations.EN.summary",
                "translations.SI.title", "translations.SI.summary",
                "translations.TA.title", "translations.TA.summary");
        assertThat(weights).containsExactlyInAnyOrderEntriesOf(Map.of(
                "title", 10F,
                "aiEnrichment.summary", 4F,
                "aiEnrichment.topics", 6F,
                "translations.EN.title", 10F,
                "translations.EN.summary", 4F,
                "translations.SI.title", 10F,
                "translations.SI.summary", 4F,
                "translations.TA.title", 10F,
                "translations.TA.summary", 4F));
        assertThat(options).containsEntry("name", ArticleTextIndexInitializer.INDEX_NAME)
                .containsEntry("default_language", "none");
        assertThat(weights.keySet()).noneMatch(field -> field.contains("extractedContent")
                || field.contains("keywords") || field.contains("entities")
                || field.contains("semanticEmbedding"));
    }

    @Test
    void createsIndexOnlyWhenNoTextIndexExists() throws Exception {
        when(mongoOperations.indexOps(Article.class)).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(List.of());

        new ArticleTextIndexInitializer(mongoOperations)
                .run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<IndexDefinition> definition = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations).ensureIndex(definition.capture());
        assertThat(definition.getValue().getIndexKeys())
                .isEqualTo(ArticleTextIndexInitializer.definition().getIndexKeys());
        assertThat(definition.getValue().getIndexOptions())
                .isEqualTo(ArticleTextIndexInitializer.definition().getIndexOptions());
    }

    @Test
    void acceptsIdenticalExistingIndexIdempotently() throws Exception {
        when(mongoOperations.indexOps(Article.class)).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(List.of(compatibleIndex()));

        new ArticleTextIndexInitializer(mongoOperations)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(indexOperations, never()).ensureIndex(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptsMongoTextIndexWireRepresentationContainingInternalFtsxKey() throws Exception {
        when(mongoOperations.indexOps(Article.class)).thenReturn(indexOperations);
        Document weights = new Document();
        ArticleTextIndexInitializer.FIELD_WEIGHTS.forEach(weights::append);
        Document mongoIndex = new Document("v", 2)
                .append("key", new Document("_fts", "text").append("_ftsx", 1))
                .append("name", ArticleTextIndexInitializer.INDEX_NAME)
                .append("weights", weights)
                .append("default_language", ArticleTextIndexInitializer.DEFAULT_LANGUAGE)
                .append("language_override", "language")
                .append("textIndexVersion", 3);
        IndexInfo atlasRepresentation = IndexInfo.indexInfoOf(mongoIndex);
        when(indexOperations.getIndexInfo()).thenReturn(List.of(atlasRepresentation));

        new ArticleTextIndexInitializer(mongoOperations)
                .run(new DefaultApplicationArguments(new String[0]));

        assertThat(atlasRepresentation.getIndexFields())
                .anyMatch(field -> "_ftsx".equals(field.getKey()) && !field.isText());
        assertThat(ArticleTextIndexInitializer.compatible(atlasRepresentation)).isTrue();
        verify(indexOperations, never()).ensureIndex(org.mockito.ArgumentMatchers.any());
        verify(indexOperations, never()).dropIndex(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void refusesIncompatibleTextIndexWithoutDeletingAnything() {
        when(mongoOperations.indexOps(Article.class)).thenReturn(indexOperations);
        IndexInfo incompatible = new IndexInfo(
                List.of(IndexField.text("extractedContent", 1F)), "old_text", false, false, "english");
        when(indexOperations.getIndexInfo()).thenReturn(List.of(incompatible));

        assertThatThrownBy(() -> new ArticleTextIndexInitializer(mongoOperations)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incompatible MongoDB text index");
        verify(indexOperations, never()).dropIndex(org.mockito.ArgumentMatchers.anyString());
        verify(indexOperations, never()).dropAllIndexes();
    }

    private IndexInfo compatibleIndex() {
        List<IndexField> fields = ArticleTextIndexInitializer.FIELD_WEIGHTS.entrySet().stream()
                .map(entry -> IndexField.text(entry.getKey(), entry.getValue())).toList();
        return new IndexInfo(fields, ArticleTextIndexInitializer.INDEX_NAME,
                false, false, ArticleTextIndexInitializer.DEFAULT_LANGUAGE);
    }
}
