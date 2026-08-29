package lk.srilankannews.article;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

class ArticlePersistenceMetadataTest {

    @Test
    void mapsToArticlesCollectionWithUniqueCanonicalUrlIndex() throws NoSuchFieldException {
        Document document = Article.class.getAnnotation(Document.class);
        Indexed canonicalUrlIndex = Article.class.getDeclaredField("canonicalUrl").getAnnotation(Indexed.class);

        assertThat(document.collection()).isEqualTo("articles");
        assertThat(canonicalUrlIndex).isNotNull();
        assertThat(canonicalUrlIndex.unique()).isTrue();
        assertThat(canonicalUrlIndex.name()).isEqualTo("uk_articles_canonical_url");
    }
}
