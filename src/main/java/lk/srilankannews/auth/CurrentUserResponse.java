/*
 * File overview: Defines data returned by Current User API operations.
 * Area: authentication and authorization.
 */
package lk.srilankannews.auth;

public record CurrentUserResponse(boolean authenticated, String userId, String email) {
}
