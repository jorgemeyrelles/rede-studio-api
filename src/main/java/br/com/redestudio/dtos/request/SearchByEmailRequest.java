package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for looking up a user by exact email address.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SearchByEmailRequest", description = "Payload for finding a user by email")
public class SearchByEmailRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Schema(description = "Email address to search for", example = "jsilva@redestudio.com.br", required = true)
    private String email;
}
