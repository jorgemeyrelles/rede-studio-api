package br.com.redestudio.dtos.request;

import br.com.redestudio.dtos.NetworkStatePayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for saving a full project snapshot (autosave).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SaveSnapshotRequest", description = "Payload for saving a project's network state snapshot")
public class SaveSnapshotRequest {

    @NotNull(message = "networkState is required")
    @Valid
    @Schema(description = "Full network state snapshot", required = true)
    private NetworkStatePayload networkState;
}
