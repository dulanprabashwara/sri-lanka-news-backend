package lk.srilankannews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.common.domain.Language;
import lk.srilankannews.source.IngestionType;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.source.api.SourceApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UserFollowServiceTest {
    @Mock UserFollowRepository repository;
    @Mock SourceService sourceService;
    @Mock lk.srilankannews.article.ArticleService articleService;
    @Mock lk.srilankannews.analytics.AnalyticsRecorder analyticsRecorder;
    private UserFollowService service;
    private final Instant now = Instant.parse("2026-09-02T03:00:00Z");
    private final Source source = new Source("source-1", "Daily Mirror", "daily-mirror",
            "https://www.dailymirror.lk", Language.EN, IngestionType.RSS, true, now, now);

    @BeforeEach
    void setUp() {
        service = new UserFollowService(repository, sourceService, articleService, new SourceApiMapper(),
                new TopicNormalizer(), Clock.fixed(now, ZoneOffset.UTC), analyticsRecorder);
    }

    @Test
    void createsAndReadsSourceFollowUsingResolvedInternalIdentity() {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(source));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.followSource("user-a", "daily-mirror").followed()).isTrue();
        ArgumentCaptor<UserFollow> follow = ArgumentCaptor.forClass(UserFollow.class);
        verify(repository).save(follow.capture());
        assertThat(follow.getValue().targetKey()).isEqualTo("source-1");
        assertThat(follow.getValue().userId()).isEqualTo("user-a");
        assertThat(follow.getValue().lastSeenAt()).isEqualTo(now);

        when(repository.findByUserIdAndTargetTypeAndTargetKey(
                "user-b", FollowTargetType.SOURCE, "source-1")).thenReturn(Optional.empty());
        assertThat(service.sourceStatus("user-b", "daily-mirror").followed()).isFalse();
    }

    @Test
    void rejectsUnknownSource() {
        when(sourceService.findBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.followSource("user-a", "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateFollowRaceReturnsExistingCreationTime() {
        UserFollow existing = new UserFollow("follow-1", "user-a", FollowTargetType.TOPIC,
                "cricket", "Cricket", now.minusSeconds(60));
        when(repository.save(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(repository.findByUserIdAndTargetTypeAndTargetKey(
                "user-a", FollowTargetType.TOPIC, "cricket")).thenReturn(Optional.of(existing));
        assertThat(service.followTopic("user-a", " CRICKET ").followedAt())
                .isEqualTo(now.minusSeconds(60));
    }

    @Test
    void topicIdentityNormalizesCaseAndWhitespaceWhilePreservingReadableLabel() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.followTopic("user-a", " Drug   Trafficking ");
        ArgumentCaptor<UserFollow> follow = ArgumentCaptor.forClass(UserFollow.class);
        verify(repository).save(follow.capture());
        assertThat(follow.getValue().targetKey()).isEqualTo("drug trafficking");
        assertThat(follow.getValue().displayLabel()).isEqualTo("Drug Trafficking");
    }

    @Test
    void unfollowIsIdempotentAndStrictlyOwnerScoped() {
        service.unfollowTopic("user-b", "Cricket");
        verify(repository).deleteByUserIdAndTargetTypeAndTargetKey(
                "user-b", FollowTargetType.TOPIC, "cricket");
    }

    @Test
    void batchStatusResolvesSourcesOnceAndQueriesFollowsInBatches() {
        when(sourceService.findAllBySlugs(List.of("daily-mirror"))).thenReturn(List.of(source));
        UserFollow sourceFollow = new UserFollow("f1", "user-a", FollowTargetType.SOURCE,
                "source-1", "Daily Mirror", now);
        UserFollow topicFollow = new UserFollow("f2", "user-a", FollowTargetType.TOPIC,
                "cricket", "Cricket", now);
        when(repository.findAllByUserIdAndTargetTypeAndTargetKeyIn(
                eq("user-a"), eq(FollowTargetType.SOURCE), any())).thenReturn(List.of(sourceFollow));
        when(repository.findAllByUserIdAndTargetTypeAndTargetKeyIn(
                eq("user-a"), eq(FollowTargetType.TOPIC), any())).thenReturn(List.of(topicFollow));
        FollowBatchStatusResponse result = service.batchStatus("user-a",
                new FollowBatchStatusRequest(List.of("daily-mirror"), List.of("Cricket", "Elections")));
        assertThat(result.sources()).singleElement().extracting(SourceFollowStatusResponse::followed)
                .isEqualTo(true);
        assertThat(result.topics()).extracting(TopicFollowStatusResponse::followed)
                .containsExactly(true, false);
        verify(sourceService).findAllBySlugs(List.of("daily-mirror"));
    }

    @Test
    void listUsesOwnerFilterDeterministicSortAndBatchSourceHydration() {
        UserFollow sourceFollow = new UserFollow("f1", "user-a", FollowTargetType.SOURCE,
                "source-1", "Daily Mirror", now);
        UserFollow deletedSource = new UserFollow("f2", "user-a", FollowTargetType.SOURCE,
                "deleted", "Old Source", now.minusSeconds(1));
        when(repository.findByUserIdAndTargetType(eq("user-a"), eq(FollowTargetType.SOURCE),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(sourceFollow, deletedSource)));
        when(sourceService.findAllByIds(Set.of("source-1", "deleted"))).thenReturn(List.of(source));
        var result = service.list("user-a", 0, 20, FollowTargetType.SOURCE);
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).source().slug()).isEqualTo("daily-mirror");
        assertThat(result.content().get(1).source()).isNull();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdAndTargetType(eq("user-a"),
                eq(FollowTargetType.SOURCE), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        verify(sourceService).findAllByIds(Set.of("source-1", "deleted"));
    }

    @Test
    void listCalculatesBatchNewArticleCountsPerSourceAndLeavesTopicsNull() {
        UserFollow sourceFollow = new UserFollow("f1", "user-a", FollowTargetType.SOURCE,
                "source-1", "Daily Mirror", now.minusSeconds(3600), now.minusSeconds(1800));
        UserFollow topicFollow = new UserFollow("f2", "user-a", FollowTargetType.TOPIC,
                "cricket", "Cricket", now.minusSeconds(3600), null);
        when(repository.findByUserId(eq("user-a"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourceFollow, topicFollow)));
        when(sourceService.findAllByIds(Set.of("source-1"))).thenReturn(List.of(source));
        when(articleService.countNewArticlesBySource(eq(java.util.Map.of("source-1", now.minusSeconds(1800)))))
                .thenReturn(java.util.Map.of("source-1", 4L));

        var result = service.list("user-a", 0, 20, null);
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).newArticleCount()).isEqualTo(4L);
        assertThat(result.content().get(1).newArticleCount()).isNull();
    }

    @Test
    void legacyFollowWithoutLastSeenAtUsesNowAsBaselineAndPersists() {
        UserFollow legacySourceFollow = new UserFollow("f1", "user-a", FollowTargetType.SOURCE,
                "source-1", "Daily Mirror", now.minusSeconds(86400), null);
        when(repository.findByUserId(eq("user-a"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(legacySourceFollow)));
        when(sourceService.findAllByIds(Set.of("source-1"))).thenReturn(List.of(source));
        when(articleService.countNewArticlesBySource(eq(java.util.Map.of("source-1", now))))
                .thenReturn(java.util.Map.of("source-1", 0L));

        var result = service.list("user-a", 0, 20, null);
        assertThat(result.content().get(0).newArticleCount()).isEqualTo(0L);

        ArgumentCaptor<List<UserFollow>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).lastSeenAt()).isEqualTo(now);
    }

    @Test
    void legacyFollowRegressionTestEndToEnd() {
        // 1. Legacy SOURCE follow with lastSeenAt = null
        UserFollow legacyFollow = new UserFollow("f1", "user-a", FollowTargetType.SOURCE,
                "source-1", "Daily Mirror", now.minusSeconds(86400), null);
        when(repository.findByUserId(eq("user-a"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(legacyFollow)));
        when(sourceService.findAllByIds(Set.of("source-1"))).thenReturn(List.of(source));
        when(articleService.countNewArticlesBySource(eq(java.util.Map.of("source-1", now))))
                .thenReturn(java.util.Map.of("source-1", 0L));

        // First list: 0 historical new articles, baseline becomes persisted
        var firstResult = service.list("user-a", 0, 20, null);
        assertThat(firstResult.content().get(0).newArticleCount()).isEqualTo(0L);

        ArgumentCaptor<List<UserFollow>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saveCaptor.capture());
        UserFollow persistedFollow = saveCaptor.getValue().get(0);
        assertThat(persistedFollow.lastSeenAt()).isEqualTo(now);

        // 2. Second list after article arrives (repository returns persisted follow with lastSeenAt = now)
        when(repository.findByUserId(eq("user-a"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(persistedFollow)));
        when(articleService.countNewArticlesBySource(eq(java.util.Map.of("source-1", now))))
                .thenReturn(java.util.Map.of("source-1", 1L));

        var secondResult = service.list("user-a", 0, 20, null);
        assertThat(secondResult.content().get(0).newArticleCount()).isEqualTo(1L);

        // 3. Third list without acknowledgement: newArticleCount remains 1
        var thirdResult = service.list("user-a", 0, 20, null);
        assertThat(thirdResult.content().get(0).newArticleCount()).isEqualTo(1L);

        // 4. markSourceSeen resets count to 0
        Instant ackTime = now.plusSeconds(1800);
        UserFollowService ackService = new UserFollowService(repository, sourceService,
                articleService, new SourceApiMapper(), new TopicNormalizer(),
                Clock.fixed(ackTime, ZoneOffset.UTC), analyticsRecorder);
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(source));
        when(repository.findByUserIdAndTargetTypeAndTargetKey("user-a", FollowTargetType.SOURCE, "source-1"))
                .thenReturn(Optional.of(persistedFollow));

        ackService.markSourceSeen("user-a", "daily-mirror");
        ArgumentCaptor<UserFollow> singleCaptor = ArgumentCaptor.forClass(UserFollow.class);
        verify(repository).save(singleCaptor.capture());
        UserFollow seenFollow = singleCaptor.getValue();
        assertThat(seenFollow.lastSeenAt()).isEqualTo(ackTime);

        // 5. Query with new baseline returns 0
        when(repository.findByUserId(eq("user-a"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(seenFollow)));
        when(articleService.countNewArticlesBySource(eq(java.util.Map.of("source-1", ackTime))))
                .thenReturn(java.util.Map.of("source-1", 0L));
        var ackResult = ackService.list("user-a", 0, 20, null);
        assertThat(ackResult.content().get(0).newArticleCount()).isEqualTo(0L);

        // 6. New article arrives afterward -> count becomes 1
        when(articleService.countNewArticlesBySource(eq(java.util.Map.of("source-1", ackTime))))
                .thenReturn(java.util.Map.of("source-1", 1L));
        var postAckResult = ackService.list("user-a", 0, 20, null);
        assertThat(postAckResult.content().get(0).newArticleCount()).isEqualTo(1L);
    }

    @Test
    void markSourceSeenUpdatesLastSeenAtToCurrentTime() {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(source));
        UserFollow existing = new UserFollow("f1", "user-a", FollowTargetType.SOURCE,
                "source-1", "Daily Mirror", now.minusSeconds(3600), now.minusSeconds(1800));
        when(repository.findByUserIdAndTargetTypeAndTargetKey("user-a", FollowTargetType.SOURCE, "source-1"))
                .thenReturn(Optional.of(existing));

        service.markSourceSeen("user-a", "daily-mirror");

        ArgumentCaptor<UserFollow> captor = ArgumentCaptor.forClass(UserFollow.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().lastSeenAt()).isEqualTo(now);
    }

    @Test
    void markSourceSeenThrowsNotFoundWhenNotFollowed() {
        when(sourceService.findBySlug("daily-mirror")).thenReturn(Optional.of(source));
        when(repository.findByUserIdAndTargetTypeAndTargetKey("user-a", FollowTargetType.SOURCE, "source-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markSourceSeen("user-a", "daily-mirror"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
