package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.EquipmentPrice;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response representation of an equipment's embedded price sub-document.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "EquipmentPriceResponse", description = "Approximate price in USD and BRL, scanned on the same date")
public class EquipmentPriceResponse {

    @Schema(description = "Approximate price in USD", example = "999.00")
    private BigDecimal approxPriceUsd;

    @Schema(description = "Approximate price in BRL", example = "5144.85")
    private BigDecimal approxPriceBrl;

    @Schema(description = "Date the price was looked up (UTC)")
    private Instant scannedAt;

    public static EquipmentPriceResponse from(EquipmentPrice price) {
        if (price == null) {
            return null;
        }
        return new EquipmentPriceResponse(price.getApproxPriceUsd(), price.getApproxPriceBrl(), price.getScannedAt());
    }
}
