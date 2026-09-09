package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;

/**
 * Public representation of a user account — never exposes {@code passwordHash}.
 *
 * <p>Created via {@link #from(UserEntity)} to keep mapping logic
 * centralised and avoid accidental field leakage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UserResponse", description = "User profile data returned by management endpoints")
public class UserResponse {

    @Schema(description = "MongoDB document id (hex string)", example = "663f1a2b3c4d5e6f7a8b9c0d")
    private String id;

    @Schema(description = "Unique display name", example = "jsilva")
    private String username;

    @Schema(description = "Unique email address", example = "jsilva@redestudio.com.br")
    private String email;

    @Schema(description = "Roles assigned to the user", example = "[\"USER\"]")
    private Set<String> roles;

    @Schema(description = "Whether the account is active", example = "true")
    private boolean active;

    @Schema(description = "Preferred UI language (ISO 639-1)", example = "pt")
    private String preferredLanguage;

    @Schema(description = "Account creation timestamp (UTC)")
    private Instant createdAt;

    @Schema(description = "Last update timestamp (UTC)")
    private Instant updatedAt;

    /**
     * Maps a {@link UserEntity} to a {@link UserResponse}, omitting sensitive fields.
     *
     * @param entity source entity (must not be null)
     * @return a new {@link UserResponse} instance
     */
    public static UserResponse from(UserEntity entity) {
        return new UserResponse(
                entity.getId().toHexString(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getRoles(),
                entity.isActive(),
                entity.getPreferredLanguage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
