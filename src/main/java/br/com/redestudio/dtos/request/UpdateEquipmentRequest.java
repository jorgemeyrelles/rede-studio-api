package br.com.redestudio.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

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

    @Size(min = 1, message = "Function must not be empty")
    @Schema(description = "New functions, if changing (replaces the whole list)", example = "[\"access point\"]")
    private List<@NotBlank(message = "Function entries must not be blank") String> function;

    @Valid
    @Schema(description = "New price, if changing")
    private EquipmentPriceRequest price;
}
