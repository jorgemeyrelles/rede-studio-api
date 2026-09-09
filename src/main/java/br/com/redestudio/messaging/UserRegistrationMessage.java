package br.com.redestudio.messaging;

import java.util.Set;

/**
 * Payload published to the {@code user.registration} RabbitMQ queue when a
 * new account is accepted by {@code AuthService#register} (password signup)
 * or {@code AuthService#loginOrRegisterOAuth} (Google/Microsoft signup) —
 * same domain event, "a new account was accepted", regardless of origin.
 *
 * <p>The consumer ({@link UserRegistrationConsumer}) is responsible for the
 * actual MongoDB persistence and for triggering the welcome email — the
 * synchronous request path only claims the Redis idempotency keys and emits
 * this message.
 *
 * <p>{@code passwordHash} is {@code null} for an OAuth signup;
 * {@code oauthProvider}/{@code oauthProviderId} are {@code null} for a
 * password signup — exactly one of the two identity mechanisms is ever set.
 */
public record UserRegistrationMessage(
        String id,
        String username,
        String email,
        String passwordHash,
        Set<String> roles,
        String oauthProvider,
        String oauthProviderId) {
}
