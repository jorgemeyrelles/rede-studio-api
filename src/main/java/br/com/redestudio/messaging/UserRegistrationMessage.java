package br.com.redestudio.messaging;

import java.util.Set;

/**
 * Payload published to the {@code user.registration} RabbitMQ queue when a
 * new account is accepted by {@code AuthService#register}.
 *
 * <p>The consumer ({@link UserRegistrationConsumer}) is responsible for the
 * actual MongoDB persistence and for triggering the welcome email — the
 * synchronous request path only claims the Redis idempotency keys and emits
 * this message.
 */
public record UserRegistrationMessage(
        String username,
        String email,
        String passwordHash,
        Set<String> roles) {
}
