package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** Price sub-document of a cloud price item — both currencies plus the FX quote used. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudPriceValueRequest", description = "Price in USD and BRL, with the USD→BRL quote used")
public class CloudPriceValueRequest {

    @NotNull(message = "usd is required")
    @DecimalMin(value = "0", message = "usd must be >= 0")
    @Schema(description = "Price published by the provider, in USD", example = "0.0168", required = true)
    private BigDecimal usd;

    @NotNull(message = "brl is required")
    @DecimalMin(value = "0", message = "brl must be >= 0")
    @Schema(description = "usd × fxRate", example = "0.091476", required = true)
    private BigDecimal brl;

    @NotNull(message = "fxRate is required")
    @DecimalMin(value = "0", inclusive = false, message = "fxRate must be > 0")
    @Schema(description = "USD→BRL rate used (PTAX venda)", example = "5.445", required = true)
    private BigDecimal fxRate;

    @NotNull(message = "fxQuotedAt is required")
    @Schema(description = "Date of the FX quote (UTC)", required = true)
    private Instant fxQuotedAt;
}
