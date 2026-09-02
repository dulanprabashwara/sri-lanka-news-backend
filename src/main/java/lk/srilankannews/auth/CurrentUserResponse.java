package lk.srilankannews.auth;

public record CurrentUserResponse(boolean authenticated, String userId, String email) {
}
