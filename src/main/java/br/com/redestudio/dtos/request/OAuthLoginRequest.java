package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for the OAuth social login endpoint.
 * The frontend obtains this ID token client-side from the Google/Microsoft
 * SDK — the backend only verifies it, it never talks to the provider directly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "OAuthLoginRequest", description = "Payload for Google/Microsoft social login")
public class OAuthLoginRequest {

    @NotBlank(message = "idToken is required")
    @Schema(description = "ID token issued by the provider's SDK", required = true)
    private String idToken;
}
