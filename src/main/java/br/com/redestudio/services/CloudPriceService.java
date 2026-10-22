package br.com.redestudio.services;

import br.com.redestudio.dtos.request.CloudPriceBulkUpsertRequest;
import br.com.redestudio.dtos.request.CloudPriceItemRequest;
import br.com.redestudio.dtos.response.CloudPriceBulkUpsertResponse;
import br.com.redestudio.dtos.response.CloudPriceCatalogItem;
import br.com.redestudio.dtos.response.CloudPriceCatalogResponse;
import br.com.redestudio.dtos.response.CloudPriceMetaResponse;
import br.com.redestudio.dtos.response.CloudPriceRegionSummary;
import br.com.redestudio.dtos.response.CloudPriceResponse;
import br.com.redestudio.entities.CloudPriceEntity;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.messaging.CloudPriceBulkUpsertMessage;
import br.com.redestudio.messaging.CloudPriceMutationProducer;
import br.com.redestudio.repositories.CloudPriceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Cloud price catalog: reads (Redis first, Mongo on a miss) for the
 * Studio's cost engine and region selector, and the ADMIN bulk upsert used
 * by {@code PriceLoadCli} to load/refresh prices in production (locally,
 * {@code CloudPriceMigration} writes on boot).
 *
 * <p>Writes follow the LB → Redis → queue → Mongo rule: the request thread
 * evicts the affected cache keys and publishes batches of
 * {@link #BATCH_SIZE} to {@code cloud-price.bulk-upsert};
 * {@code CloudPriceMutationConsumer} upserts them into Mongo and evicts
 * again after the write.
 */
@ApplicationScoped
public class CloudPriceService {

    static final int BATCH_SIZE = 500;

    @Inject
    CloudPriceRepository repository;

    @Inject
    CloudPriceCacheService cacheService;

    @Inject
    CloudPriceMutationProducer producer;

    /** Regions with prices loaded, per provider (region selector + "prices from" footer). */
    public CloudPriceMetaResponse meta() {
        return cacheService.getMeta().orElseGet(() -> {
            List<CloudPriceRegionSummary> regions = repository.summarizeRegions().stream()
                    .map(CloudPriceService::toRegionSummary)
                    .toList();
            CloudPriceMetaResponse meta = new CloudPriceMetaResponse(regions);
            cacheService.putMeta(meta);
            return meta;
        });
    }

    /**
     * Every price entry of one region, compact. An unknown region returns
     * an empty catalog (not 404): the frontend shows "no prices for this
     * region" instead of failing.
     */
    public CloudPriceCatalogResponse catalog(CloudProvider provider, String region) {
        return cacheService.getCatalog(provider, region).orElseGet(() -> {
            List<CloudPriceEntity> entries = repository.findByRegion(provider, region);
            CloudPriceEntity latest = entries.stream()
                    .filter(e -> e.getUpdatedAt() != null)
                    .max(Comparator.comparing(CloudPriceEntity::getUpdatedAt))
                    .orElse(null);
            CloudPriceCatalogResponse catalog = new CloudPriceCatalogResponse(
                    provider,
                    region,
                    entries.stream().map(CloudPriceEntity::getRegionName).filter(Objects::nonNull).findFirst().orElse(null),
                    latest != null ? latest.getUpdatedAt() : null,
                    latest != null ? latest.getPrice().getFxRate() : null,
                    latest != null ? latest.getPrice().getFxQuotedAt() : null,
                    entries.stream().map(CloudPriceCatalogItem::from).toList());
            if (!entries.isEmpty()) {
                cacheService.putCatalog(catalog); // não cacheia região vazia — pode ser carregada a qualquer momento
            }
            return catalog;
        });
    }

    /** Full entries of one resource type in one region (read straight from Mongo — small, rarely called). */
    public List<CloudPriceResponse> search(CloudProvider provider, String region, String resourceType) {
        return repository.findByResourceType(provider, region, resourceType).stream()
                .map(CloudPriceResponse::from)
                .toList();
    }

    /** Accepts a batch of prices for asynchronous upsert. */
    public CloudPriceBulkUpsertResponse bulkUpsert(CloudPriceBulkUpsertRequest request) {
        List<CloudPriceItemRequest> items = request.getItems();

        items.stream()
                .map(item -> new RegionKey(item.getProvider(), item.getRegion()))
                .distinct()
                .forEach(key -> cacheService.evict(key.provider(), key.region()));

        int batches = 0;
        for (int start = 0; start < items.size(); start += BATCH_SIZE) {
            List<CloudPriceItemRequest> batch = items.subList(start, Math.min(start + BATCH_SIZE, items.size()));
            producer.publishBulkUpsert(new CloudPriceBulkUpsertMessage(List.copyOf(batch)));
            batches++;
        }
        return new CloudPriceBulkUpsertResponse(items.size(), batches);
    }

    private record RegionKey(CloudProvider provider, String region) {
    }

    private static CloudPriceRegionSummary toRegionSummary(Document doc) {
        Document id = doc.get("_id", Document.class);
        Number count = doc.get("itemCount", Number.class);
        Instant updatedAt = CloudPriceRepository.instant(doc.get("updatedAt"));
        return new CloudPriceRegionSummary(
                CloudProvider.valueOf(id.getString("provider")),
                id.getString("region"),
                doc.getString("regionName"),
                count != null ? count.longValue() : 0,
                updatedAt,
                CloudPriceRepository.bigDecimal(doc.get("fxRate")),
                CloudPriceRepository.instant(doc.get("fxQuotedAt")));
    }
}
