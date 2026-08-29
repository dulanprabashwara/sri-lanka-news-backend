package lk.srilankannews.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

class SourcePersistenceMetadataTest {

    @Test
    void mapsToSourcesCollectionWithUniqueSlugIndex() throws NoSuchFieldException {
        Document document = Source.class.getAnnotation(Document.class);
        Indexed slugIndex = Source.class.getDeclaredField("slug").getAnnotation(Indexed.class);

        assertThat(document.collection()).isEqualTo("sources");
        assertThat(slugIndex).isNotNull();
        assertThat(slugIndex.unique()).isTrue();
        assertThat(slugIndex.name()).isEqualTo("uk_sources_slug");
    }
}
