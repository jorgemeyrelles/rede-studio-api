package br.com.redestudio.services;

import br.com.redestudio.dtos.response.CloudPriceCatalogResponse;
import br.com.redestudio.dtos.response.CloudPriceMetaResponse;
import br.com.redestudio.entities.CloudProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis read cache for the cloud price catalog (cache-aside, 24h TTL):
 * one key per {@code (provider, region)} catalog plus one for the meta
 * summary. Prices only change on a {@code bulk-upsert}, which evicts the
 * affected keys (on the request thread <em>and</em> again in the consumer,
 * after the Mongo write — otherwise a read between the two could cache
 * the old prices for 24h).
 *
 * <p>Failures are swallowed and logged, same as {@code EquipmentCacheService}:
 * MongoDB is the source of truth.
 */
@ApplicationScoped
public class CloudPriceCacheService {

    private static final Logger LOG = Logger.getLogger(CloudPriceCacheService.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String META_KEY = "cloud-prices:meta";

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    private ValueCommands<String, String> valueCommands;
    private KeyCommands<String> keyCommands;

    @PostConstruct
    void init() {
        valueCommands = redisDataSource.value(String.class);
        keyCommands = redisDataSource.key();
    }

    private static String catalogKey(CloudProvider provider, String region) {
        return "cloud-prices:" + provider.name() + ":" + region;
    }

    public Optional<CloudPriceCatalogResponse> getCatalog(CloudProvider provider, String region) {
        return read(catalogKey(provider, region), CloudPriceCatalogResponse.class);
    }

    public void putCatalog(CloudPriceCatalogResponse catalog) {
        write(catalogKey(catalog.getProvider(), catalog.getRegion()), catalog);
    }

    public Optional<CloudPriceMetaResponse> getMeta() {
        return read(META_KEY, CloudPriceMetaResponse.class);
    }

    public void putMeta(CloudPriceMetaResponse meta) {
        write(META_KEY, meta);
    }

    /** Evicts one region's catalog and the meta summary (both change on a reload). */
    public void evict(CloudProvider provider, String region) {
        try {
            keyCommands.del(catalogKey(provider, region), META_KEY);
        } catch (Exception e) {
            LOG.warnf(e, "Redis evict failed for %s/%s", provider, region);
        }
    }

    private <T> void write(String key, T value) {
        try {
            valueCommands.set(key, objectMapper.writeValueAsString(value));
            keyCommands.expire(key, TTL.toSeconds());
        } catch (JsonProcessingException e) {
            LOG.warnf(e, "Failed to serialize value for cache key %s", key);
        } catch (Exception e) {
            LOG.warnf(e, "Redis write failed for key %s", key);
        }
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        try {
            String raw = valueCommands.get(key);
            return raw == null ? Optional.empty() : Optional.of(objectMapper.readValue(raw, type));
        } catch (Exception e) {
            LOG.warnf(e, "Redis read failed for key %s", key);
            return Optional.empty();
        }
    }
}
