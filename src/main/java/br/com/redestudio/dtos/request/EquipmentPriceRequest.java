package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request payload for an equipment's embedded price sub-document — always
 * both currencies plus the date they were looked up together, never a bare
 * number.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "EquipmentPriceRequest", description = "Approximate price in USD and BRL, scanned on the same date")
public class EquipmentPriceRequest {

    @NotNull(message = "approxPriceUsd is required")
    @Schema(description = "Approximate price in USD", example = "999.00", required = true)
    private BigDecimal approxPriceUsd;

    @NotNull(message = "approxPriceBrl is required")
    @Schema(description = "Approximate price in BRL", example = "5144.85", required = true)
    private BigDecimal approxPriceBrl;

    @NotNull(message = "scannedAt is required")
    @Schema(description = "Date the price was looked up (UTC)", required = true)
    private Instant scannedAt;
}
