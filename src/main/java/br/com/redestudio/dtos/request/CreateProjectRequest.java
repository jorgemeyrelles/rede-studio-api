package br.com.redestudio.dtos.request;

import br.com.redestudio.dtos.CloudStatePayload;
import br.com.redestudio.dtos.NetworkStatePayload;
import br.com.redestudio.entities.NetworkType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
 *
 * <p>{@code networkType} is chosen once here and never editable afterward
 * — no other request DTO in this package carries it (see {@link NetworkType}).
 *
 * <p>The initial state depends on {@code networkType}: a {@code CLOUD}
 * project sends {@code cloudState} (and no {@code networkState}); every
 * other type sends {@code networkState} (and no {@code cloudState}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateProjectRequest", description = "Payload for creating a new project")
public class CreateProjectRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Project display name", example = "Rede Matriz SP", required = true)
    private String name;

    @NotNull(message = "networkType is required")
    @Schema(description = "Network topology category, immutable after creation", required = true)
    private NetworkType networkType;

    @Valid
    @Schema(description = "Full initial network state snapshot — required unless networkType is CLOUD")
    private NetworkStatePayload networkState;

    @Valid
    @Schema(description = "Initial cloud architecture draft — required when networkType is CLOUD")
    private CloudStatePayload cloudState;

    /** Exactly the state that matches {@code networkType} must be present. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "CLOUD projects require cloudState (and no networkState); other types require networkState (and no cloudState)")
    public boolean isStateMatchingNetworkType() {
        if (networkType == null) {
            return true; // já reportado pelo @NotNull de networkType
        }
        return networkType == NetworkType.CLOUD
                ? cloudState != null && networkState == null
                : networkState != null && cloudState == null;
    }
}
