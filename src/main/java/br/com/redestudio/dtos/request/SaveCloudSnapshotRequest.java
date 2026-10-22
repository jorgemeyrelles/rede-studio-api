package br.com.redestudio.dtos.request;

import br.com.redestudio.dtos.CloudStatePayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for saving a CLOUD project's full architecture draft (autosave).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SaveCloudSnapshotRequest", description = "Payload for saving a CLOUD project's architecture draft")
public class SaveCloudSnapshotRequest {

    @NotNull(message = "cloudState is required")
    @Valid
    @Schema(description = "Full cloud architecture draft", required = true)
    private CloudStatePayload cloudState;
}
