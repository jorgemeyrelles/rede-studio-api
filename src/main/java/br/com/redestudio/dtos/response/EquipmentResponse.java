package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.EquipmentEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Full equipment catalog entry representation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "EquipmentResponse", description = "Equipment catalog entry")
public class EquipmentResponse {

    @Schema(description = "MongoDB document id (hex string)", example = "663f1a2b3c4d5e6f7a8b9c0d")
    private String id;

    @Schema(description = "Equipment manufacturer", example = "Cisco")
    private String brand;

    @Schema(description = "Equipment model name/code", example = "Catalyst 9120AXE")
    private String model;

    @Schema(description = "Functions the equipment serves", example = "[\"roteador\", \"firewall\", \"gateway\"]")
    private List<String> function;

    @Schema(description = "Approximate price (USD + BRL)")
    private EquipmentPriceResponse price;

    @Schema(description = "Creation timestamp (UTC)")
    private Instant createdAt;

    @Schema(description = "Last update timestamp (UTC)")
    private Instant updatedAt;

    public static EquipmentResponse from(EquipmentEntity equipment) {
        return new EquipmentResponse(
                equipment.getId().toHexString(),
                equipment.getBrand(),
                equipment.getModel(),
                equipment.getFunction(),
                EquipmentPriceResponse.from(equipment.getPrice()),
                equipment.getCreatedAt(),
                equipment.getUpdatedAt());
    }
}
