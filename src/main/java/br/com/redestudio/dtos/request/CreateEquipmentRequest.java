package br.com.redestudio.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Request body for registering a single piece of equipment in the catalog.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateEquipmentRequest", description = "Payload for creating a single equipment catalog entry")
public class CreateEquipmentRequest {

    @NotBlank(message = "Brand is required")
    @Schema(description = "Equipment manufacturer", example = "Cisco", required = true)
    private String brand;

    @NotBlank(message = "Model is required")
    @Schema(description = "Equipment model name/code", example = "Catalyst 9120AXE", required = true)
    private String model;

    @NotEmpty(message = "Function is required")
    @Schema(description = "Functions the equipment serves", example = "[\"roteador\", \"firewall\", \"gateway\"]", required = true)
    private List<@NotBlank(message = "Function entries must not be blank") String> function;

    @NotNull(message = "price is required")
    @Valid
    @Schema(description = "Approximate price (USD + BRL)", required = true)
    private EquipmentPriceRequest price;
}
