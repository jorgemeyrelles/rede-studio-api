package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for looking up users by partial, case-insensitive username match.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SearchByNameRequest", description = "Payload for finding users by partial name")
public class SearchByNameRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Partial, case-insensitive match against username", example = "silv", required = true)
    private String name;
}
