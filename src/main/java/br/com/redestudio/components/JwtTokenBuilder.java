package br.com.redestudio.components;

import br.com.redestudio.configurations.JwtConfiguration;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtSignatureException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Component responsible for generating and inspecting JWT tokens.
 *
 * <p><b>Token generation</b> — {@link #generateToken} signs a new JWT using
 * the RSA private key configured at {@code app.jwt.private-key-location}.
 * SmallRye JWT Build reads the key file path via the standard
 * {@code smallrye.jwt.sign.key.location} property (mapped in application.yml).
 *
 * <p><b>Token inspection</b> — {@link #extractSubject} and {@link #extractRoles}
 * operate on an already-verified {@link JsonWebToken} injected by the Quarkus
 * security layer after the filter validates the {@code Authorization: Bearer}
 * header. Verification itself is handled automatically by SmallRye JWT using
 * the RSA public key at {@code mp.jwt.verify.publickey.location}.
 *
 * <p>Key locations are mounted as Docker volumes in dev and injected via
 * AWS Secrets Manager in master — no key material inside the JAR.
 */
@ApplicationScoped
public class JwtTokenBuilder {

    @Inject
    JwtConfiguration jwtConfiguration;

    /**
     * Generates a signed JWT for the given user.
     *
     * <p>Claims set:
     * <ul>
     *   <li>{@code iss} — issuer from configuration</li>
     *   <li>{@code sub} — user email (stable unique identifier)</li>
     *   <li>{@code upn} — user email (MicroProfile JWT principal name)</li>
     *   <li>{@code uid} — user's Mongo {@code _id} (hex string) — lets other
     *       entities (e.g. {@code ProjectEntity.ownerId}) reference the user
     *       by ID instead of email. See {@code AuthService#register} for why
     *       this is generated client-side ahead of the actual Mongo insert.</li>
     *   <li>{@code groups} — roles set, used by {@code @RolesAllowed}</li>
     *   <li>{@code preferred_username} — display name</li>
     *   <li>{@code iat} — issued-at (now)</li>
     *   <li>{@code exp} — expiration (now + configured lifetime)</li>
     * </ul>
     *
     * @param subject  the user's email address
     * @param userId   the user's Mongo {@code _id} as a hex string
     * @param username the user's display name
     * @param roles    set of role strings (e.g. {@code {"USER"}})
     * @return signed JWT string ready to be returned in {@code AuthResponse}
     * @throws IllegalStateException if signing fails (key not found or invalid)
     */
    public String generateToken(String subject, String userId, String username, Set<String> roles) {
        try {
            return Jwt.issuer(jwtConfiguration.issuer())
                    .subject(subject)
                    .upn(subject)
                    .claim("uid", userId)
                    .claim("preferred_username", username)
                    .groups(roles)
                    .issuedAt(Instant.now())
                    .expiresIn(Duration.ofSeconds(jwtConfiguration.expirationSeconds()))
                    .sign();
        } catch (JwtSignatureException e) {
            throw new IllegalStateException(
                    "JWT signing failed — check private key location configuration", e);
        }
    }

    /**
     * Extracts the subject ({@code sub} claim) from a verified JWT.
     *
     * <p>The token must have already been validated by the SmallRye JWT filter.
     * This method does NOT perform signature verification.
     *
     * @param token the verified JWT injected by the Quarkus security context
     * @return the subject claim (user email)
     */
    public String extractSubject(JsonWebToken token) {
        return token.getSubject();
    }

    /**
     * Extracts the roles ({@code groups} claim) from a verified JWT.
     *
     * <p>The token must have already been validated by the SmallRye JWT filter.
     *
     * @param token the verified JWT injected by the Quarkus security context
     * @return the set of role strings assigned to the token owner
     */
    public Set<String> extractRoles(JsonWebToken token) {
        return token.getGroups();
    }

    /**
     * Returns the configured token lifetime in seconds.
     * Exposed so that {@code AuthService} can include it in {@code AuthResponse}.
     *
     * @return expiration duration in seconds
     */
    public long getExpirationSeconds() {
        return jwtConfiguration.expirationSeconds();
    }
}

