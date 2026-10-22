package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.CloudProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Every price entry of one provider in one region, plus when they (and the FX quote) are from. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudPriceCatalogResponse", description = "Price catalog of one region")
public class CloudPriceCatalogResponse {

    private CloudProvider provider;
    private String region;
    private String regionName;

    @Schema(description = "Most recent updated_at among the region's entries; null when the region has no prices")
    private Instant updatedAt;

    private BigDecimal fxRate;
    private Instant fxQuotedAt;
    private List<CloudPriceCatalogItem> items;
}
