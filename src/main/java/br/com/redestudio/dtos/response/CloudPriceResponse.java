package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.CloudPrice;
import br.com.redestudio.entities.CloudPriceEntity;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.entities.PriceSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Full representation of one cloud price entry. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudPriceResponse", description = "One cloud price dimension, with USD and BRL")
public class CloudPriceResponse {

    private String id;
    private CloudProvider provider;
    private String serviceCode;
    private String resourceType;
    private String region;
    private String regionName;
    private String sku;
    private String rateCode;
    private String description;
    private Map<String, Object> attributes;
    private String unit;
    private CloudPrice price;
    private BigDecimal tierBegin;
    private BigDecimal tierEnd;
    private String pricingModel;
    private Instant effectiveDate;
    private PriceSource source;
    private Instant createdAt;
    private Instant updatedAt;

    public static CloudPriceResponse from(CloudPriceEntity e) {
        return new CloudPriceResponse(
                e.getId(), e.getProvider(), e.getServiceCode(), e.getResourceType(), e.getRegion(),
                e.getRegionName(), e.getSku(), e.getRateCode(), e.getDescription(), e.getAttributes(),
                e.getUnit(), e.getPrice(), e.getTierBegin(), e.getTierEnd(), e.getPricingModel(),
                e.getEffectiveDate(), e.getSource(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
