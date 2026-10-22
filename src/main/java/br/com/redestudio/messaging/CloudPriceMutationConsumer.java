package br.com.redestudio.messaging;

import br.com.redestudio.dtos.request.CloudPriceItemRequest;
import br.com.redestudio.entities.CloudPriceEntity;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.repositories.CloudPriceRepository;
import br.com.redestudio.services.CloudPriceCacheService;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Applies cloud price batches to MongoDB (upsert by {@code (provider,
 * rate_code)}) and evicts the affected region catalogs from Redis after
 * the write — see {@link CloudPriceCacheService} for why the eviction is
 * repeated here.
 */
@ApplicationScoped
public class CloudPriceMutationConsumer {

    private static final Logger LOG = Logger.getLogger(CloudPriceMutationConsumer.class);

    @Inject
    CloudPriceRepository repository;

    @Inject
    CloudPriceCacheService cacheService;

    @Incoming("cloud-price-bulk-upsert-in")
    public void onBulkUpsert(JsonObject payload) {
        CloudPriceBulkUpsertMessage message = payload.mapTo(CloudPriceBulkUpsertMessage.class);
        List<CloudPriceEntity> entities = message.items().stream().map(CloudPriceItemRequest::toEntity).toList();

        int written = repository.bulkUpsert(entities);
        message.items().stream()
                .map(item -> new RegionKey(item.getProvider(), item.getRegion()))
                .distinct()
                .forEach(key -> cacheService.evict(key.provider(), key.region()));
        LOG.infof("cloud-price.bulk-upsert: %d item(s) received, %d inserted/modified", entities.size(), written);
    }

    private record RegionKey(CloudProvider provider, String region) {
    }
}
