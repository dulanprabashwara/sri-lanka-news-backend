/*
 * File overview: Represents a specific Admin Retry Not Allowed failure.
 * Area: administrative operations and monitoring.
 */
package lk.srilankannews.admin;

public class AdminRetryNotAllowedException extends RuntimeException {
    public AdminRetryNotAllowedException() {
        super("Only a failed Article can be retried.");
    }
}
