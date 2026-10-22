package br.com.redestudio.repositories;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.entities.CloudPrice;
import br.com.redestudio.entities.CloudPriceEntity;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.entities.PriceSource;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Data access for {@code cloud_prices} in the separate cloud database
 * ({@code app.cloud-pricing.database}).
 *
 * <p>Uses the {@link MongoClient} directly (not Panache): Panache's
 * {@code @MongoEntity(database = ...)} is a compile-time literal, and this
 * database name differs per profile (dev/test/master). Documents are mapped
 * explicitly — BigDecimal ↔ {@link Decimal128}, Instant ↔ {@link Date} —
 * so the stored shape is exactly the documented contract (snake_case).
 */
@ApplicationScoped
public class CloudPriceRepository {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "app.cloud-pricing.database")
    String databaseName;

    private MongoCollection<Document> collection() {
        return mongoClient.getDatabase(databaseName).getCollection(CollectionNames.CLOUD_PRICES);
    }

    /** Idempotent — called on every boot by {@code StartupRunner}. */
    public void ensureIndexes() {
        collection().createIndex(
                Indexes.ascending("provider", "rate_code"),
                new IndexOptions().unique(true).name("provider_rate_code_unique"));
        collection().createIndex(Indexes.ascending("provider", "region", "resource_type"));
    }

    /** Coleção inexistente ou sem documentos — critério da migration local de preços. */
    public boolean isEmpty() {
        return collection().countDocuments() == 0;
    }

    public String databaseName() {
        return databaseName;
    }

    /** Every price entry of one provider in one region (the cost engine's catalog). */
    public List<CloudPriceEntity> findByRegion(CloudProvider provider, String region) {
        return find(Filters.and(Filters.eq("provider", provider.name()), Filters.eq("region", region)));
    }

    /** Price entries of one resource type in one region (inspector option lists). */
    public List<CloudPriceEntity> findByResourceType(CloudProvider provider, String region, String resourceType) {
        return find(Filters.and(
                Filters.eq("provider", provider.name()),
                Filters.eq("region", region),
                Filters.eq("resource_type", resourceType)));
    }

    /**
     * One summary document per {@code (provider, region)}: region name,
     * item count, latest {@code updated_at}, and the FX quote of the most
     * recently updated item — feeds the region selector and the "prices
     * from dd/mm/yyyy" footer.
     */
    public List<Document> summarizeRegions() {
        return collection().aggregate(List.of(
                Aggregates.sort(Sorts.descending("updated_at")),
                Aggregates.group(
                        new Document("provider", "$provider").append("region", "$region"),
                        Accumulators.first("regionName", "$region_name"),
                        Accumulators.sum("itemCount", 1),
                        Accumulators.max("updatedAt", "$updated_at"),
                        Accumulators.first("fxRate", "$price.fx_rate"),
                        Accumulators.first("fxQuotedAt", "$price.fx_quoted_at")),
                Aggregates.sort(Sorts.ascending("_id.provider", "_id.region"))))
                .into(new ArrayList<>());
    }

    /**
     * Upserts by {@code (provider, rate_code)}: inserts set
     * {@code created_at}; every write sets {@code updated_at} and replaces
     * the price fields. Unordered, so one bad document doesn't stop the
     * rest of the batch.
     *
     * @return number of documents inserted or modified
     */
    public int bulkUpsert(List<CloudPriceEntity> items) {
        if (items.isEmpty()) {
            return 0;
        }
        Date now = new Date();
        List<WriteModel<Document>> writes = new ArrayList<>(items.size());
        for (CloudPriceEntity item : items) {
            Bson key = Filters.and(
                    Filters.eq("provider", item.getProvider().name()),
                    Filters.eq("rate_code", item.getRateCode()));
            List<Bson> sets = new ArrayList<>();
            toFields(item).forEach((field, value) -> sets.add(Updates.set(field, value)));
            sets.add(Updates.set("updated_at", now));
            sets.add(Updates.setOnInsert("created_at", now));
            writes.add(new UpdateOneModel<>(key, Updates.combine(sets), new UpdateOptions().upsert(true)));
        }
        var result = collection().bulkWrite(writes, new BulkWriteOptions().ordered(false));
        return result.getUpserts().size() + result.getModifiedCount();
    }

    private List<CloudPriceEntity> find(Bson filter) {
        List<CloudPriceEntity> items = new ArrayList<>();
        for (Document doc : collection().find(filter).sort(Sorts.ascending("resource_type", "sku"))) {
            items.add(fromDocument(doc));
        }
        return items;
    }

    // -----------------------------------------------------------------------
    //  Mapping
    // -----------------------------------------------------------------------

    /** Every field except the key/timestamps, as stored (snake_case). */
    private static Document toFields(CloudPriceEntity item) {
        CloudPrice price = item.getPrice();
        Document priceDoc = new Document()
                .append("usd", decimal(price.getUsd()))
                .append("brl", decimal(price.getBrl()))
                .append("fx_rate", decimal(price.getFxRate()))
                .append("fx_quoted_at", date(price.getFxQuotedAt()));

        PriceSource source = item.getSource();
        Document sourceDoc = source == null ? null : new Document()
                .append("offer_code", source.getOfferCode())
                .append("publication_version", source.getPublicationVersion())
                .append("url", source.getUrl());

        return new Document()
                .append("service_code", item.getServiceCode())
                .append("resource_type", item.getResourceType())
                .append("region", item.getRegion())
                .append("region_name", item.getRegionName())
                .append("sku", item.getSku())
                .append("description", item.getDescription())
                .append("attributes", item.getAttributes() != null ? new Document(item.getAttributes()) : new Document())
                .append("unit", item.getUnit())
                .append("price", priceDoc)
                .append("tier_begin", decimal(item.getTierBegin()))
                .append("tier_end", decimal(item.getTierEnd()))
                .append("pricing_model", item.getPricingModel())
                .append("effective_date", date(item.getEffectiveDate()))
                .append("source", sourceDoc);
    }

    @SuppressWarnings("unchecked")
    private static CloudPriceEntity fromDocument(Document doc) {
        CloudPriceEntity item = new CloudPriceEntity();
        item.setId(doc.getObjectId("_id").toHexString());
        item.setProvider(CloudProvider.valueOf(doc.getString("provider")));
        item.setServiceCode(doc.getString("service_code"));
        item.setResourceType(doc.getString("resource_type"));
        item.setRegion(doc.getString("region"));
        item.setRegionName(doc.getString("region_name"));
        item.setSku(doc.getString("sku"));
        item.setRateCode(doc.getString("rate_code"));
        item.setDescription(doc.getString("description"));
        Object attributes = doc.get("attributes");
        item.setAttributes(attributes instanceof Map ? (Map<String, Object>) attributes : Map.of());
        item.setUnit(doc.getString("unit"));

        Document price = doc.get("price", Document.class);
        if (price != null) {
            item.setPrice(new CloudPrice(
                    bigDecimal(price.get("usd")),
                    bigDecimal(price.get("brl")),
                    bigDecimal(price.get("fx_rate")),
                    instant(price.get("fx_quoted_at"))));
        }

        item.setTierBegin(bigDecimal(doc.get("tier_begin")));
        item.setTierEnd(bigDecimal(doc.get("tier_end")));
        item.setPricingModel(doc.getString("pricing_model"));
        item.setEffectiveDate(instant(doc.get("effective_date")));

        Document source = doc.get("source", Document.class);
        if (source != null) {
            item.setSource(new PriceSource(
                    source.getString("offer_code"),
                    source.getString("publication_version"),
                    source.getString("url")));
        }

        item.setCreatedAt(instant(doc.get("created_at")));
        item.setUpdatedAt(instant(doc.get("updated_at")));
        return item;
    }

    private static Decimal128 decimal(BigDecimal value) {
        return value == null ? null : new Decimal128(value);
    }

    private static Date date(Instant value) {
        return value == null ? null : Date.from(value);
    }

    /** Public so callers mapping aggregation output share the same conversion. */
    public static BigDecimal bigDecimal(Object value) {
        if (value instanceof Decimal128 d) {
            return d.bigDecimalValue();
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return null;
    }

    public static Instant instant(Object value) {
        return value instanceof Date d ? d.toInstant() : null;
    }
}
