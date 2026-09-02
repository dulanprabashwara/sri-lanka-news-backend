package lk.srilankannews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import lk.srilankannews.article.ArticleCategory;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.article.api.ArticleApiMapper;
import lk.srilankannews.article.api.ArticleLocalizationService;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.story.StoryRepository;
import lk.srilankannews.story.Story;
import lk.srilankannews.story.api.StoryApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class UserBookmarkServiceTest {
    @Mock UserBookmarkRepository repository;
    @Mock ArticleRepository articleRepository;
    @Mock StoryRepository storyRepository;
    @Mock SourceService sourceService;
    @Mock ArticleApiMapper articleApiMapper;
    @Mock StoryApiMapper storyApiMapper;
    @Mock ArticleLocalizationService localizationService;
    private UserBookmarkService service;
    private final Instant now = Instant.parse("2026-09-02T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new UserBookmarkService(repository, articleRepository, storyRepository,
                sourceService, articleApiMapper, storyApiMapper, localizationService,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void createsArticleBookmarkForAuthenticatedOwner() {
        when(articleRepository.existsById("article-1")).thenReturn(true);
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            UserBookmark value = invocation.getArgument(0);
            return new UserBookmark("bookmark-1", value.userId(), value.targetType(),
                    value.targetId(), value.createdAt());
        });
        BookmarkStatusResponse response = service.create(
                "user-a", BookmarkTargetType.ARTICLE, "article-1");
        assertThat(response.bookmarked()).isTrue();
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    void duplicateKeyRaceReturnsExistingBookmarkState() {
        UserBookmark existing = new UserBookmark(
                "bookmark-1", "user-a", BookmarkTargetType.ARTICLE, "article-1", now);
        when(articleRepository.existsById("article-1")).thenReturn(true);
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(repository.findByUserIdAndTargetTypeAndTargetId(
                "user-a", BookmarkTargetType.ARTICLE, "article-1"))
                .thenReturn(Optional.of(existing));
        assertThat(service.create("user-a", BookmarkTargetType.ARTICLE, "article-1").bookmarked())
                .isTrue();
    }

    @Test
    void rejectsMissingTargets() {
        when(articleRepository.existsById("missing")).thenReturn(false);
        assertThatThrownBy(() -> service.create("user-a", BookmarkTargetType.ARTICLE, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createsOnlyPublicStoryBookmarks() {
        Story publicStory = story("story-1", 1);
        when(storyRepository.findById("story-1")).thenReturn(Optional.of(publicStory));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.create("user-a", BookmarkTargetType.STORY, "story-1").bookmarked())
                .isTrue();

        when(storyRepository.findById("pending")).thenReturn(Optional.of(story("pending", 0)));
        assertThatThrownBy(() -> service.create("user-a", BookmarkTargetType.STORY, "pending"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void statusAndDeletionAreStrictlyOwnerScoped() {
        when(repository.findByUserIdAndTargetTypeAndTargetId(
                "user-b", BookmarkTargetType.ARTICLE, "article-1"))
                .thenReturn(Optional.empty());
        assertThat(service.status("user-b", BookmarkTargetType.ARTICLE, "article-1").bookmarked())
                .isFalse();
        service.delete("user-b", BookmarkTargetType.ARTICLE, "article-1");
        verify(repository).deleteByUserIdAndTargetTypeAndTargetId(
                "user-b", BookmarkTargetType.ARTICLE, "article-1");
    }

    @Test
    void listsOnlyOwnersFilteredBookmarksWithBoundedDeterministicPagingAndBatchHydration() {
        UserBookmark bookmark = new UserBookmark("bookmark-1", "user-a",
                BookmarkTargetType.STORY, "missing-legacy-story", now);
        when(repository.findByUserIdAndTargetType(eq("user-a"), eq(BookmarkTargetType.STORY),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(bookmark)));
        when(articleRepository.findAllById(any())).thenReturn(List.of());
        when(storyRepository.findAllById(any())).thenReturn(List.of());
        when(sourceService.findAllByIds(any())).thenReturn(List.of());

        var response = service.list("user-a", 0, 20, BookmarkTargetType.STORY, null);

        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.targetId()).isEqualTo("missing-legacy-story");
            assertThat(item.story()).isNull();
        });
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdAndTargetType(eq("user-a"),
                eq(BookmarkTargetType.STORY), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
        verify(articleRepository).findAllById(any());
        verify(storyRepository).findAllById(any());
        verify(sourceService).findAllByIds(any());
    }

    private Story story(String id, long articleCount) {
        return new Story(id, "Story", "representative", ArticleCategory.LOCAL, now, now,
                articleCount, Set.of("source-1"), Set.of(), now, now, "hybrid-v1");
    }
}
