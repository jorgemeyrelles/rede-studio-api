package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for the login endpoint.
 * Provides credentials to be validated by {@code AuthService}.
 * On success, the service returns an {@code AuthResponse} containing a signed JWT.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LoginRequest", description = "Credentials for user authentication")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Schema(description = "Registered email address", example = "jsilva@redestudio.com.br", required = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password", example = "S3cur3P@ss", required = true)
    private String password;
}
