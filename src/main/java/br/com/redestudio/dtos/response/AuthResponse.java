package br.com.redestudio.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;

/**
 * Response returned after a successful login or registration.
 *
 * <p>The {@code token} field carries the signed JWT and must be sent
 * in subsequent requests as:
 * <pre>Authorization: Bearer {token}</pre>
 *
 * <p>The filter layer ({@code JwtAuthenticationFilter}) and the SmallRye JWT
 * verifier will use this token to authenticate and authorise protected routes.
 *
 * <p>{@code id}/{@code preferredLanguage}/{@code createdAt} are included so
 * the frontend can build a full user profile from this response alone,
 * without an immediate follow-up {@code GET /api/users/me} — which would
 * otherwise race the asynchronous registration consumer (see
 * {@link br.com.redestudio.services.AuthService#register}) and 404 if the
 * document hasn't landed in MongoDB yet. All three values are already known
 * synchronously at this point (id is pre-generated, preferred language is
 * always the "pt" default on a new account, createdAt is "now") — for
 * login, they're read straight off the already-fetched {@code UserEntity}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthResponse", description = "JWT token issued after successful authentication")
public class AuthResponse {

    @Schema(description = "Signed JWT — send as 'Authorization: Bearer {token}'",
            example = "eyJhbGciOiJSUzI1NiJ9...")
    private String token;

    @Schema(description = "Token type — always 'Bearer'", example = "Bearer")
    private String tokenType;

    @Schema(description = "Token lifetime in seconds", example = "86400")
    private long expiresIn;

    @Schema(description = "MongoDB document id (hex string) — same value as the JWT 'uid' claim",
            example = "663f1a2b3c4d5e6f7a8b9c0d")
    private String id;

    @Schema(description = "Authenticated user's display name", example = "jsilva")
    private String username;

    @Schema(description = "Roles assigned to the user — used as JWT 'groups' claim",
            example = "[\"USER\"]")
    private Set<String> roles;

    @Schema(description = "Preferred UI language (ISO 639-1)", example = "pt")
    private String preferredLanguage;

    @Schema(description = "Account creation timestamp (UTC)")
    private Instant createdAt;
}
