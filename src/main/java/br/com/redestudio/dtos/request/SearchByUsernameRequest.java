package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for looking up a user by exact username.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SearchByUsernameRequest", description = "Payload for finding a user by username")
public class SearchByUsernameRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Username to search for", example = "jsilva", required = true)
    private String username;
}
