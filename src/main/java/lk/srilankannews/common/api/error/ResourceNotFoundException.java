/*
 * File overview: Represents a specific Resource Not Found failure.
 * Area: shared API and domain utilities.
 */
package lk.srilankannews.common.api.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName) {
        super(resourceName + " was not found.");
    }
}
