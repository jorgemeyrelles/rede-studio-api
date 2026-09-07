package br.com.redestudio.services;

import br.com.redestudio.components.JwtTokenBuilder;
import br.com.redestudio.components.PasswordHasher;
import br.com.redestudio.configurations.JwtConfiguration;
import br.com.redestudio.dtos.request.LoginRequest;
import br.com.redestudio.dtos.request.RegisterRequest;
import br.com.redestudio.dtos.response.AuthResponse;
import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.exceptions.InvalidCredentialsException;
import br.com.redestudio.exceptions.UserAlreadyExistsException;
import br.com.redestudio.messaging.UserRegistrationMessage;
import br.com.redestudio.messaging.UserRegistrationProducer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Set;

/**
 * Service responsible for user registration and authentication.
 *
 * <p>Orchestrates the full auth flow:
 * <ol>
 *   <li>Input validation (uniqueness checks before persisting)</li>
 *   <li>Password hashing via {@link PasswordHasher}</li>
 *   <li>User persistence via {@link UserService}</li>
 *   <li>JWT generation via {@link JwtTokenBuilder}</li>
 * </ol>
 *
 * <p>Throws domain exceptions ({@link UserAlreadyExistsException},
 * {@link InvalidCredentialsException}) that are mapped to HTTP responses
 * by {@code GlobalExceptionHandler}.
 */
@ApplicationScoped
public class AuthService {

    /**
     * TTL of the Redis registration-dedup claim. Not a uniqueness guarantee
     * by itself — see {@link IdempotencyService} — just long enough to
     * absorb double-submits/retries while the queue consumer catches up.
     */
    private static final Duration REGISTRATION_CLAIM_TTL = Duration.ofMinutes(10);

    @Inject
    UserService userService;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    JwtTokenBuilder jwtTokenBuilder;

    @Inject
    JwtConfiguration jwtConfiguration;

    @Inject
    IdempotencyService idempotencyService;

    @Inject
    UserRegistrationProducer userRegistrationProducer;

    /**
     * Accepts a new user registration and returns a signed JWT immediately.
     *
     * <p>Follows the LB → Redis → fila → BD pattern: this method only claims
     * the Redis idempotency keys (fast dedup, no MongoDB round-trip) and
     * publishes a {@link UserRegistrationMessage} to the {@code user.registration}
     * queue — the actual MongoDB persistence and welcome email happen
     * asynchronously in {@link br.com.redestudio.messaging.UserRegistrationConsumer}.
     *
     * <p>The JWT is self-contained (signed from data already in hand), so it
     * can be returned before the MongoDB document exists. This trades a small
     * eventual-consistency window — a login attempt in the same instant the
     * queue is backlogged could momentarily not find the user yet — for a
     * synchronous path that never blocks on MongoDB.
     *
     * @param request registration payload (username, email, password)
     * @return {@link AuthResponse} containing the signed JWT and user info
     * @throws UserAlreadyExistsException if the email or username was already
     *         claimed within the last {@link #REGISTRATION_CLAIM_TTL}
     */
    public AuthResponse register(RegisterRequest request) {
        String emailKey = "idempotency:register:email:" + request.getEmail().toLowerCase();
        String usernameKey = "idempotency:register:username:" + request.getUsername().toLowerCase();

        if (!idempotencyService.claim(emailKey, REGISTRATION_CLAIM_TTL)) {
            throw new UserAlreadyExistsException(
                    "Email already registered: " + request.getEmail());
        }

        if (!idempotencyService.claim(usernameKey, REGISTRATION_CLAIM_TTL)) {
            idempotencyService.release(emailKey);
            throw new UserAlreadyExistsException(
                    "Username already taken: " + request.getUsername());
        }

        String passwordHash = passwordHasher.hash(request.getPassword());
        Set<String> roles = Set.of("USER");

        userRegistrationProducer.publish(
                new UserRegistrationMessage(request.getUsername(), request.getEmail(), passwordHash, roles));

        String token = jwtTokenBuilder.generateToken(request.getEmail(), request.getUsername(), roles);

        return new AuthResponse(
                token,
                "Bearer",
                jwtConfiguration.expirationSeconds(),
                request.getUsername(),
                roles);
    }

    /**
     * Authenticates a user with email and password credentials.
     *
     * <p>Steps:
     * <ol>
     *   <li>Look up the user by email — not found means invalid credentials</li>
     *   <li>Verify the BCrypt hash — mismatch means invalid credentials</li>
     *   <li>Check account is active</li>
     *   <li>Generate and return a signed JWT</li>
     * </ol>
     *
     * <p>Both "user not found" and "wrong password" throw the same
     * {@link InvalidCredentialsException} to prevent user enumeration.
     *
     * @param request login payload (email, password)
     * @return {@link AuthResponse} containing the signed JWT and user info
     * @throws InvalidCredentialsException if credentials are invalid or account is inactive
     */
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userService.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordHasher.verify(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtTokenBuilder.generateToken(
                user.getEmail(),
                user.getUsername(),
                user.getRoles());

        return new AuthResponse(
                token,
                "Bearer",
                jwtConfiguration.expirationSeconds(),
                user.getUsername(),
                user.getRoles());
    }
}
