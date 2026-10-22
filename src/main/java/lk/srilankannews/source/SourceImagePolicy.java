/*
 * File overview: Defines the rules used by Source Image.
 * Area: publisher source registration and APIs.
 */
package lk.srilankannews.source;

import java.util.Set;

public record SourceImagePolicy(
        boolean enabled,
        Set<String> allowedHosts
) {
    public SourceImagePolicy {
        allowedHosts = allowedHosts == null ? Set.of() : Set.copyOf(allowedHosts);
    }
}
