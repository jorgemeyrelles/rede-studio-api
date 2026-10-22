package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.CloudPriceEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Compact price entry for the frontend's cost engine — only what the
 * calculation needs (no ids, source or timestamps: those are per-region
 * metadata on {@link CloudPriceCatalogResponse}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudPriceCatalogItem", description = "Compact price entry used by the cost engine")
public class CloudPriceCatalogItem {

    private String resourceType;
    private String sku;
    private String rateCode;
    private String description;
    private Map<String, Object> attributes;
    private String unit;
    private BigDecimal usd;
    private BigDecimal brl;
    private BigDecimal tierBegin;
    private BigDecimal tierEnd;

    public static CloudPriceCatalogItem from(CloudPriceEntity e) {
        return new CloudPriceCatalogItem(
                e.getResourceType(), e.getSku(), e.getRateCode(), e.getDescription(), e.getAttributes(),
                e.getUnit(), e.getPrice().getUsd(), e.getPrice().getBrl(), e.getTierBegin(), e.getTierEnd());
    }
}
