package br.com.redestudio.entities;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * One price dimension of a cloud product — e.g. "t3.micro Linux On-Demand
 * in sa-east-1, per hour". Lives in the separate cloud database
 * ({@code app.cloud-pricing.database}, collection {@code cloud_prices}),
 * not in the main application database: it's shared reference data with
 * its own lifecycle (loaded by {@code CloudPriceMigration} locally and by
 * {@code PriceLoadCli} in production), never
 * user data.
 *
 * <p>Not a Panache {@code @MongoEntity}: the database name comes from
 * config, so {@code CloudPriceRepository} talks to the {@code MongoClient}
 * directly and maps documents explicitly.
 *
 * <p>Natural key: {@code (provider, rateCode)} — reloading upserts by it,
 * keeping {@code createdAt} from the first load and advancing
 * {@code updatedAt}.
 */
@Data
@NoArgsConstructor
public class CloudPriceEntity {

    private String id;

    /** Provider family — {@code AWS | AZURE | GCP | OCI}. */
    private CloudProvider provider;

    /** Provider service code, e.g. {@code AmazonEC2}. */
    private String serviceCode;

    /** Studio catalog key, e.g. {@code aws.ec2.instance}, {@code aws.vpc.nat_gateway}. */
    private String resourceType;

    /** Region code ({@code sa-east-1}) or {@code global}. */
    private String region;

    /** Human region name as published, e.g. {@code South America (Sao Paulo)}. */
    private String regionName;

    private String sku;

    /** Unique per provider — on AWS, {@code sku.offerTermCode.rateCode}. */
    private String rateCode;

    private String description;

    /** Opaque provider attributes: instanceType, vcpu, memory, operatingSystem... */
    private Map<String, Object> attributes;

    /** Billing unit: {@code Hrs}, {@code GB-Mo}, {@code GB}, {@code LCU-Hrs}... */
    private String unit;

    private CloudPrice price;

    /** Tiered pricing range (e.g. data transfer); {@code null} when flat. */
    private BigDecimal tierBegin;

    private BigDecimal tierEnd;

    /** {@code ON_DEMAND} for now. */
    private String pricingModel;

    /** Effective date published by the provider. */
    private Instant effectiveDate;

    private PriceSource source;

    private Instant createdAt;

    private Instant updatedAt;
}
