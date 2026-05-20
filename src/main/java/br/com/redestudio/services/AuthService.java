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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

    @Inject
    UserService userService;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    JwtTokenBuilder jwtTokenBuilder;

    @Inject
    JwtConfiguration jwtConfiguration;

    /**
     * Registers a new user account and returns a signed JWT on success.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate email and username uniqueness</li>
     *   <li>Hash the plain-text password with BCrypt</li>
     *   <li>Persist the new {@link UserEntity} with role {@code USER}</li>
     *   <li>Generate and return a signed JWT via {@link JwtTokenBuilder}</li>
     * </ol>
     *
     * @param request registration payload (username, email, password)
     * @return {@link AuthResponse} containing the signed JWT and user info
     * @throws UserAlreadyExistsException if the email or username is already taken
     */
    public AuthResponse register(RegisterRequest request) {
        if (userService.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "Email already registered: " + request.getEmail());
        }

        if (userService.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(
                    "Username already taken: " + request.getUsername());
        }

        String passwordHash = passwordHasher.hash(request.getPassword());
        Set<String> roles = Set.of("USER");

        UserEntity user = UserEntity.create(
                request.getUsername(),
                request.getEmail(),
                passwordHash,
                roles);

        userService.createUser(user);

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
