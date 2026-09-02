package lk.srilankannews.auth;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("news.admin")
public record AdminProperties(String userIds) {

    public Set<String> parsedUserIds() {
        if (userIds == null || userIds.isBlank()) return Set.of();
        return Arrays.stream(userIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
