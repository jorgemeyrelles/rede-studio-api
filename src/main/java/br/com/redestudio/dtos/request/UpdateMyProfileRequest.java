package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for self-service profile updates ({@code PATCH /api/users/me}).
 *
 * <p>Deliberately narrower than {@link UpdateUserRequest} (the ADMIN-only
 * variant): no email, roles or active status here — a user editing their
 * own profile can only change their display name and preferred language.
 *
 * <p>All fields are optional — only non-null fields are applied.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateMyProfileRequest", description = "Payload for self-service profile updates (username and preferred language only)")
public class UpdateMyProfileRequest {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Schema(description = "New unique display name (omit to leave unchanged)", example = "jsilva")
    private String username;

    @Schema(description = "New preferred UI language, ISO 639-1 (omit to leave unchanged)", example = "pt")
    private String preferredLanguage;
}
