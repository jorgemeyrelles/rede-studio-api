package br.com.redestudio.services;

import br.com.redestudio.dtos.response.EquipmentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Redis-backed read cache for the equipment catalog — the fast path that
 * {@code EquipmentService} reads/writes on every request, per the
 * "LB → Redis → fila → BD" pattern (see {@code CLAUDE.md}). Written
 * synchronously by the request thread on every write, so a read
 * immediately following a write is already consistent without waiting for
 * {@code EquipmentMutationConsumer} to catch up with MongoDB.
 *
 * <p>Unlike {@code ProjectCacheService}, there's no ownership scoping here
 * — the whole catalog is a single shared list (equivalent in shape to one
 * "owner"'s project list), so there's only one list key instead of one per
 * owner.
 *
 * <p>Cache failures (Redis down, serialization error) are swallowed and
 * logged, never thrown — Redis here is a performance/consistency
 * optimization, not the durable source of truth (MongoDB is), so a caller
 * always has to be prepared to fall back to Mongo on a miss.
 */
@ApplicationScoped
public class EquipmentCacheService {

    private static final Logger LOG = Logger.getLogger(EquipmentCacheService.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String ALL_KEY = "equipments:all";

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

    private static String equipmentKey(String id) {
        return "equipment:" + id;
    }

    public Optional<EquipmentResponse> getOne(String id) {
        return read(equipmentKey(id), EquipmentResponse.class);
    }

    public void putOne(EquipmentResponse equipment) {
        write(equipmentKey(equipment.getId()), equipment);
    }

    public void evictOne(String id) {
        keyCommands.del(equipmentKey(id));
    }

    public Optional<List<EquipmentResponse>> getAll() {
        String raw = safeGet(ALL_KEY);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(raw, new TypeReference<List<EquipmentResponse>>() { }));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to deserialize cached equipment list");
            return Optional.empty();
        }
    }

    public void putAll(List<EquipmentResponse> items) {
        try {
            String json = objectMapper.writeValueAsString(items);
            valueCommands.set(ALL_KEY, json);
            keyCommands.expire(ALL_KEY, TTL.toSeconds());
        } catch (JsonProcessingException e) {
            LOG.warnf(e, "Failed to serialize equipment list");
        }
    }

    public void evictAll() {
        keyCommands.del(ALL_KEY);
    }

    private <T> void write(String key, T value) {
        try {
            valueCommands.set(key, objectMapper.writeValueAsString(value));
            keyCommands.expire(key, TTL.toSeconds());
        } catch (JsonProcessingException e) {
            LOG.warnf(e, "Failed to serialize value for cache key %s", key);
        }
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        String raw = safeGet(key);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to deserialize cached value for key %s", key);
            return Optional.empty();
        }
    }

    private String safeGet(String key) {
        try {
            return valueCommands.get(key);
        } catch (Exception e) {
            LOG.warnf(e, "Redis read failed for key %s", key);
            return null;
        }
    }
}
