package br.com.redestudio.dtos.request;

import br.com.redestudio.dtos.NetworkStatePayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for creating a new project.
 *
 * <p>{@code networkState} is the full initial snapshot as built by the
 * frontend — the backend never needs to know its inner shape, only that
 * the required top-level fields are present (see {@link NetworkStatePayload}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateProjectRequest", description = "Payload for creating a new project")
public class CreateProjectRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Project display name", example = "Rede Matriz SP", required = true)
    private String name;

    @NotNull(message = "networkState is required")
    @Valid
    @Schema(description = "Full initial network state snapshot", required = true)
    private NetworkStatePayload networkState;
}
