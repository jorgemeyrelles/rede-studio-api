package br.com.redestudio.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

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

    @Schema(description = "Authenticated user's display name", example = "jsilva")
    private String username;

    @Schema(description = "Roles assigned to the user — used as JWT 'groups' claim",
            example = "[\"USER\"]")
    private Set<String> roles;
}
