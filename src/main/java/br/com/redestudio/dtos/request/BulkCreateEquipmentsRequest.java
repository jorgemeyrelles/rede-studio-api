package br.com.redestudio.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Request body for registering many equipment catalog entries at once.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BulkCreateEquipmentsRequest", description = "Payload for creating many equipment catalog entries in one call")
public class BulkCreateEquipmentsRequest {

    @NotEmpty(message = "items must not be empty")
    @Valid
    @Schema(description = "Equipment entries to create", required = true)
    private List<CreateEquipmentRequest> items;
}
