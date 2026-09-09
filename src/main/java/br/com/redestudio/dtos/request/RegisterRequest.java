package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for the user registration endpoint.
 * All fields are validated before the service layer is reached.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RegisterRequest", description = "Payload for new user registration")
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Schema(description = "Unique display name", example = "jsilva", required = true)
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Schema(description = "Unique email address", example = "jsilva@redestudio.com.br", required = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(description = "Plain-text password (hashed before storage)", example = "S3cur3P@ss", required = true)
    private String password;
}
