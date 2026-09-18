package br.com.redestudio.dtos.request;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for partially updating an equipment catalog entry — all
 * fields optional, only non-null ones are applied (same convention as
 * {@code UpdateUserRequest}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateEquipmentRequest", description = "Payload for partially updating an equipment catalog entry")
public class UpdateEquipmentRequest {

    @Schema(description = "New brand, if changing", example = "Cisco")
    private String brand;

    @Schema(description = "New model name/code, if changing", example = "Catalyst 9130AXI")
    private String model;

    @Schema(description = "New function/category, if changing", example = "access point")
    private String function;

    @Valid
    @Schema(description = "New price, if changing")
    private EquipmentPriceRequest price;
}
