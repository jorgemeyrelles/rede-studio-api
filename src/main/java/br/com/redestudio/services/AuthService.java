package br.com.redestudio.services;

import br.com.redestudio.components.JwtTokenBuilder;
import br.com.redestudio.components.OAuthTokenVerifier;
import br.com.redestudio.components.OAuthTokenVerifier.OAuthIdentity;
import br.com.redestudio.components.PasswordHasher;
import br.com.redestudio.configurations.JwtConfiguration;
import br.com.redestudio.dtos.request.LoginRequest;
import br.com.redestudio.dtos.request.RegisterRequest;
import br.com.redestudio.dtos.response.AuthResponse;
import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.exceptions.InvalidCredentialsException;
import br.com.redestudio.exceptions.OAuthVerificationException;
import br.com.redestudio.exceptions.UserAlreadyExistsException;
import br.com.redestudio.messaging.UserRegistrationMessage;
import br.com.redestudio.messaging.UserRegistrationProducer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;

import java.time.Duration;
import java.time.Instant;
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

    @Inject
    OAuthTokenVerifier oAuthTokenVerifier;

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

        // Gerado aqui (chamada local, sem tocar o Mongo) porque o JWT precisa
        // do _id real do usuário AGORA, de forma síncrona — o documento em si
        // só é persistido depois, de forma assíncrona, pelo consumer da fila.
        // O mesmo ObjectId viaja na mensagem e é usado como _id explícito na
        // hora de persistir, garantindo que os dois valores nunca divirjam.
        ObjectId userId = new ObjectId();
        Instant now = Instant.now();

        userRegistrationProducer.publish(
                new UserRegistrationMessage(
                        userId.toHexString(), request.getUsername(), request.getEmail(), passwordHash, roles,
                        null, null));

        String token = jwtTokenBuilder.generateToken(
                request.getEmail(), userId.toHexString(), request.getUsername(), roles);

        return new AuthResponse(
                token,
                "Bearer",
                jwtConfiguration.expirationSeconds(),
                userId.toHexString(),
                request.getUsername(),
                roles,
                "pt",
                now);
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
                user.getId().toHexString(),
                user.getUsername(),
                user.getRoles());

        return new AuthResponse(
                token,
                "Bearer",
                jwtConfiguration.expirationSeconds(),
                user.getId().toHexString(),
                user.getUsername(),
                user.getRoles(),
                user.getPreferredLanguage(),
                user.getCreatedAt());
    }

    /**
     * Authenticates via a Google/Microsoft ID token, creating or linking a
     * local account as needed. The provider only proves "this email belongs
     * to this person" — everything after verification (JWT issuance, Mongo
     * persistence) reuses the exact same machinery as {@link #register}/
     * {@link #login}, so an OAuth user is a full local user like any other.
     *
     * <ol>
     *   <li>Verify {@code idToken} via {@link OAuthTokenVerifier}.</li>
     *   <li>Already linked ({@code oauthProvider}+{@code oauthProviderId}
     *       match) → issue a JWT immediately, same as {@link #login}.</li>
     *   <li>Not linked, but a local account already uses that email → link
     *       it (the provider already proved ownership of the email, no
     *       password confirmation needed) and issue a JWT.</li>
     *   <li>No account at all → create one via the same async
     *       Redis → fila → BD path as {@link #register}, with
     *       {@code passwordHash = null} and the OAuth identity fields set.</li>
     * </ol>
     *
     * @param provider {@code "google"} or {@code "microsoft"}
     * @param idToken  the ID token JWT obtained by the frontend's SDK
     * @return {@link AuthResponse} containing the signed JWT and user info
     * @throws br.com.redestudio.exceptions.OAuthVerificationException if the
     *         token is invalid, expired, or issued for a different client
     * @throws InvalidCredentialsException if the matched/linked account is inactive
     * @throws UserAlreadyExistsException if a concurrent request is already
     *         registering the same email
     */
    public AuthResponse loginOrRegisterOAuth(String provider, String idToken) {
        String normalizedProvider = provider == null ? "" : provider.toLowerCase();
        if (!"google".equals(normalizedProvider) && !"microsoft".equals(normalizedProvider)) {
            throw new OAuthVerificationException();
        }

        OAuthIdentity identity = oAuthTokenVerifier.verify(normalizedProvider, idToken);

        UserEntity user = userService.findByOauthProviderId(normalizedProvider, identity.providerUserId())
                .orElse(null);

        if (user == null) {
            user = userService.findByEmail(identity.email()).orElse(null);
            if (user != null) {
                user.setOauthProvider(normalizedProvider);
                user.setOauthProviderId(identity.providerUserId());
                user = userService.updateUser(user);
            }
        }

        if (user != null) {
            if (!user.isActive()) {
                throw new InvalidCredentialsException();
            }
            String token = jwtTokenBuilder.generateToken(
                    user.getEmail(), user.getId().toHexString(), user.getUsername(), user.getRoles());
            return new AuthResponse(
                    token,
                    "Bearer",
                    jwtConfiguration.expirationSeconds(),
                    user.getId().toHexString(),
                    user.getUsername(),
                    user.getRoles(),
                    user.getPreferredLanguage(),
                    user.getCreatedAt());
        }

        return registerOAuthUser(normalizedProvider, identity);
    }

    /**
     * New-account branch of {@link #loginOrRegisterOAuth} — mirrors
     * {@link #register}'s async Redis → fila → BD path exactly, the only
     * differences being no password hash and a username derived from the
     * email instead of user input (there's no registration form in this
     * flow to ask for one).
     */
    private AuthResponse registerOAuthUser(String provider, OAuthIdentity identity) {
        String email = identity.email().toLowerCase();
        String emailKey = "idempotency:register:email:" + email;

        if (!idempotencyService.claim(emailKey, REGISTRATION_CLAIM_TTL)) {
            throw new UserAlreadyExistsException("Email already registered: " + email);
        }

        String baseUsername = deriveUsernameFromEmail(email);
        String username = baseUsername;
        String usernameKey = "idempotency:register:username:" + username.toLowerCase();

        if (!idempotencyService.claim(usernameKey, REGISTRATION_CLAIM_TTL)) {
            // Username derived from the email's local part isn't guaranteed
            // unique across accounts (unlike password signup, there's no form
            // here to ask the person to pick another) — disambiguate with a
            // slice of the already-unique ObjectId instead of failing signup.
            ObjectId probe = new ObjectId();
            username = baseUsername + "-" + probe.toHexString().substring(0, 6);
            usernameKey = "idempotency:register:username:" + username.toLowerCase();
            if (!idempotencyService.claim(usernameKey, REGISTRATION_CLAIM_TTL)) {
                idempotencyService.release(emailKey);
                throw new UserAlreadyExistsException("Username already taken: " + username);
            }
        }

        Set<String> roles = Set.of("USER");
        ObjectId userId = new ObjectId();
        Instant now = Instant.now();

        userRegistrationProducer.publish(
                new UserRegistrationMessage(
                        userId.toHexString(), username, email, null, roles,
                        provider, identity.providerUserId()));

        String token = jwtTokenBuilder.generateToken(email, userId.toHexString(), username, roles);

        return new AuthResponse(
                token,
                "Bearer",
                jwtConfiguration.expirationSeconds(),
                userId.toHexString(),
                username,
                roles,
                "pt",
                now);
    }

    /** Base username candidate for a new OAuth account: the email's local part, clamped to the 3-50 char range RegisterRequest also enforces. */
    private static String deriveUsernameFromEmail(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        if (localPart.length() > 50) {
            localPart = localPart.substring(0, 50);
        }
        if (localPart.length() < 3) {
            localPart = (localPart + "___").substring(0, 3);
        }
        return localPart;
    }
}
