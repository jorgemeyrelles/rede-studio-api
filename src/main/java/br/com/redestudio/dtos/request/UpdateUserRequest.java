package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Set;

/**
 * Request body for partially updating a user's profile fields.
 *
 * <p>All fields are optional — only non-null fields are applied by
 * {@code UserService.patchUser}. Password is intentionally excluded;
 * use the dedicated change-password endpoint for that.
 *
 * <p>{@code active} is a boxed {@link Boolean} (not primitive) so that
 * "field not present in the request" can be distinguished from
 * "explicitly set to false".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateUserRequest", description = "Payload for partially updating a user (excludes password)")
public class UpdateUserRequest {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Schema(description = "New unique display name (omit to leave unchanged)", example = "jsilva")
    private String username;

    @Email(message = "Email must be a valid address")
    @Schema(description = "New unique email address (omit to leave unchanged)", example = "jsilva@redestudio.com.br")
    private String email;

    @Schema(description = "New set of roles (omit to leave unchanged)", example = "[\"USER\"]")
    private Set<String> roles;

    @Schema(description = "New active status (omit to leave unchanged)", example = "true")
    private Boolean active;
}
