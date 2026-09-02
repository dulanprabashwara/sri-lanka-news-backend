package lk.srilankannews.user;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lk.srilankannews.common.api.PagedResponse;
import lk.srilankannews.common.api.error.ResourceNotFoundException;
import lk.srilankannews.source.Source;
import lk.srilankannews.source.SourceService;
import lk.srilankannews.source.api.SourceApiMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UserFollowService {
    private final UserFollowRepository repository;
    private final SourceService sourceService;
    private final SourceApiMapper sourceApiMapper;
    private final TopicNormalizer topicNormalizer;
    private final Clock clock;

    public UserFollowService(UserFollowRepository repository, SourceService sourceService,
            SourceApiMapper sourceApiMapper, TopicNormalizer topicNormalizer, Clock clock) {
        this.repository = repository;
        this.sourceService = sourceService;
        this.sourceApiMapper = sourceApiMapper;
        this.topicNormalizer = topicNormalizer;
        this.clock = clock;
    }

    public FollowStatusResponse followSource(String userId, String slug) {
        Source source = requireSource(slug);
        return create(userId, FollowTargetType.SOURCE, source.id(), source.name());
    }

    public FollowStatusResponse sourceStatus(String userId, String slug) {
        Source source = requireSource(slug);
        return status(userId, FollowTargetType.SOURCE, source.id());
    }

    public void unfollowSource(String userId, String slug) {
        Source source = requireSource(slug);
        repository.deleteByUserIdAndTargetTypeAndTargetKey(
                userId, FollowTargetType.SOURCE, source.id());
    }

    public FollowStatusResponse followTopic(String userId, String topic) {
        TopicNormalizer.NormalizedTopic normalized = topicNormalizer.normalize(topic);
        return create(userId, FollowTargetType.TOPIC, normalized.key(), normalized.label());
    }

    public FollowStatusResponse topicStatus(String userId, String topic) {
        return status(userId, FollowTargetType.TOPIC, topicNormalizer.normalize(topic).key());
    }

    public void unfollowTopic(String userId, String topic) {
        repository.deleteByUserIdAndTargetTypeAndTargetKey(
                userId, FollowTargetType.TOPIC, topicNormalizer.normalize(topic).key());
    }

    public FollowBatchStatusResponse batchStatus(
            String userId, FollowBatchStatusRequest request) {
        Map<String, Source> sourcesBySlug = sourceService.findAllBySlugs(request.sourceSlugs())
                .stream().collect(Collectors.toMap(Source::slug, Function.identity()));
        Set<String> sourceIds = sourcesBySlug.values().stream().map(Source::id)
                .collect(Collectors.toSet());
        Map<String, UserFollow> sourceFollows = followsByKey(userId, FollowTargetType.SOURCE,
                sourceIds);
        List<SourceFollowStatusResponse> sourceStatuses = request.sourceSlugs().stream()
                .map(slug -> {
                    Source source = sourcesBySlug.get(slug);
                    UserFollow follow = source == null ? null : sourceFollows.get(source.id());
                    return new SourceFollowStatusResponse(slug, follow != null,
                            follow == null ? null : follow.createdAt());
                }).toList();

        List<TopicNormalizer.NormalizedTopic> normalizedTopics = request.topics().stream()
                .map(topicNormalizer::normalize).toList();
        Set<String> topicKeys = normalizedTopics.stream().map(TopicNormalizer.NormalizedTopic::key)
                .collect(Collectors.toSet());
        Map<String, UserFollow> topicFollows = followsByKey(userId, FollowTargetType.TOPIC,
                topicKeys);
        List<TopicFollowStatusResponse> topicStatuses = IntStream.range(0, normalizedTopics.size())
                .mapToObj(index -> {
                    TopicNormalizer.NormalizedTopic topic = normalizedTopics.get(index);
                    UserFollow follow = topicFollows.get(topic.key());
                    return new TopicFollowStatusResponse(request.topics().get(index), follow != null,
                            follow == null ? null : follow.createdAt());
                }).toList();
        return new FollowBatchStatusResponse(sourceStatuses, topicStatuses);
    }

    public PagedResponse<FollowResponse> list(
            String userId, int page, int size, FollowTargetType type) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<UserFollow> follows = type == null
                ? repository.findByUserId(userId, pageable)
                : repository.findByUserIdAndTargetType(userId, type, pageable);
        Set<String> sourceIds = follows.stream()
                .filter(follow -> follow.targetType() == FollowTargetType.SOURCE)
                .map(UserFollow::targetKey).collect(Collectors.toSet());
        Map<String, Source> sources = sourceService.findAllByIds(sourceIds).stream()
                .collect(Collectors.toMap(Source::id, Function.identity()));
        List<FollowResponse> responses = follows.stream().map(follow -> {
            if (follow.targetType() == FollowTargetType.SOURCE) {
                Source source = sources.get(follow.targetKey());
                return new FollowResponse(follow.id(), follow.targetType(), follow.createdAt(),
                        source == null ? null : sourceApiMapper.toSummary(source), null);
            }
            return new FollowResponse(follow.id(), follow.targetType(), follow.createdAt(), null,
                    new FollowTopicResponse(follow.displayLabel()));
        }).toList();
        return PagedResponse.from(new PageImpl<>(responses, pageable, follows.getTotalElements()));
    }

    private FollowStatusResponse create(String userId, FollowTargetType type, String key,
            String label) {
        UserFollow follow;
        try {
            follow = repository.save(UserFollow.create(userId, type, key, label, clock.instant()));
        } catch (DuplicateKeyException exception) {
            follow = repository.findByUserIdAndTargetTypeAndTargetKey(userId, type, key)
                    .orElseThrow(() -> exception);
        }
        return new FollowStatusResponse(true, follow.createdAt());
    }

    private FollowStatusResponse status(String userId, FollowTargetType type, String key) {
        return repository.findByUserIdAndTargetTypeAndTargetKey(userId, type, key)
                .map(follow -> new FollowStatusResponse(true, follow.createdAt()))
                .orElseGet(FollowStatusResponse::absent);
    }

    private Map<String, UserFollow> followsByKey(String userId, FollowTargetType type,
            Collection<String> keys) {
        if (keys.isEmpty()) return Map.of();
        return repository.findAllByUserIdAndTargetTypeAndTargetKeyIn(userId, type, keys).stream()
                .collect(Collectors.toMap(UserFollow::targetKey, Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new));
    }

    private Source requireSource(String slug) {
        return sourceService.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Source"));
    }
}
