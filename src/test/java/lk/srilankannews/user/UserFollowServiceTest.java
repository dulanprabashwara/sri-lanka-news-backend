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
    private UserFollowService service;
    private final Instant now = Instant.parse("2026-09-02T03:00:00Z");
    private final Source source = new Source("source-1", "Daily Mirror", "daily-mirror",
            "https://www.dailymirror.lk", Language.EN, IngestionType.RSS, true, now, now);

    @BeforeEach
    void setUp() {
        service = new UserFollowService(repository, sourceService, new SourceApiMapper(),
                new TopicNormalizer(), Clock.fixed(now, ZoneOffset.UTC));
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
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
        verify(sourceService).findAllByIds(Set.of("source-1", "deleted"));
    }
}
