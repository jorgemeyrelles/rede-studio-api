package br.com.redestudio.dtos.request;

import br.com.redestudio.entities.CloudPrice;
import br.com.redestudio.entities.CloudPriceEntity;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.entities.PriceSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * One price entry in a {@code bulk-upsert} — the shape produced by
 * {@code components/pricing/AwsPriceLoader} (local migration and the
 * production load command).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudPriceItemRequest", description = "One cloud price dimension to insert or update")
public class CloudPriceItemRequest {

    @NotNull(message = "provider is required")
    @Schema(description = "Provider family", example = "AWS", required = true)
    private CloudProvider provider;

    @NotBlank(message = "serviceCode is required")
    @Schema(example = "AmazonEC2", required = true)
    private String serviceCode;

    @NotBlank(message = "resourceType is required")
    @Schema(description = "Studio catalog key", example = "aws.ec2.instance", required = true)
    private String resourceType;

    @NotBlank(message = "region is required")
    @Pattern(regexp = "[a-z0-9-]{2,32}", message = "region must be a provider region code or 'global'")
    @Schema(example = "sa-east-1", required = true)
    private String region;

    @Schema(example = "South America (Sao Paulo)")
    private String regionName;

    @NotBlank(message = "sku is required")
    private String sku;

    @NotBlank(message = "rateCode is required")
    @Schema(description = "Unique per provider (natural key of the upsert)", required = true)
    private String rateCode;

    private String description;

    @Schema(description = "Opaque provider attributes (instanceType, vcpu, memory...)")
    private Map<String, Object> attributes;

    @NotBlank(message = "unit is required")
    @Schema(example = "Hrs", required = true)
    private String unit;

    @NotNull(message = "price is required")
    @Valid
    private CloudPriceValueRequest price;

    private BigDecimal tierBegin;

    private BigDecimal tierEnd;

    @NotBlank(message = "pricingModel is required")
    @Schema(example = "ON_DEMAND", required = true)
    private String pricingModel;

    private Instant effectiveDate;

    private PriceSource source;

    /** Entidade gravada em {@code cloud_prices} (consumidor da fila e migration local). */
    public CloudPriceEntity toEntity() {
        CloudPriceEntity entity = new CloudPriceEntity();
        entity.setProvider(provider);
        entity.setServiceCode(serviceCode);
        entity.setResourceType(resourceType);
        entity.setRegion(region);
        entity.setRegionName(regionName);
        entity.setSku(sku);
        entity.setRateCode(rateCode);
        entity.setDescription(description);
        entity.setAttributes(attributes);
        entity.setUnit(unit);
        entity.setPrice(new CloudPrice(price.getUsd(), price.getBrl(), price.getFxRate(), price.getFxQuotedAt()));
        entity.setTierBegin(tierBegin);
        entity.setTierEnd(tierEnd);
        entity.setPricingModel(pricingModel);
        entity.setEffectiveDate(effectiveDate);
        entity.setSource(source);
        return entity;
    }
}
