package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for the change-password endpoint.
 * The new password is hashed by {@code PasswordHasher} before being persisted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChangePasswordRequest", description = "Payload for updating a user's password")
public class ChangePasswordRequest {

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(description = "New plain-text password (hashed before storage)",
            example = "N3wS3cur3P@ss", required = true)
    private String newPassword;
}
