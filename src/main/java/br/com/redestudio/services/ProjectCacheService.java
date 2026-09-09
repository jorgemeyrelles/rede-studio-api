package br.com.redestudio.services;

import br.com.redestudio.dtos.response.ProjectResponse;
import br.com.redestudio.dtos.response.ProjectSummaryResponse;
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
 * Redis-backed read cache for projects — the fast path that {@code
 * ProjectService} reads/writes on every request, per the
 * "LB → Redis → fila → BD" pattern (see {@code CLAUDE.md}). Written
 * synchronously by the request thread on every write, so a read
 * immediately following a write is already consistent without waiting for
 * {@code ProjectMutationConsumer} to catch up with MongoDB.
 *
 * <p>Cache failures (Redis down, serialization error) are swallowed and
 * logged, never thrown — Redis here is a performance/consistency
 * optimization, not the durable source of truth (MongoDB is), so a caller
 * always has to be prepared to fall back to Mongo on a miss.
 */
@ApplicationScoped
public class ProjectCacheService {

    private static final Logger LOG = Logger.getLogger(ProjectCacheService.class);
    private static final Duration TTL = Duration.ofHours(24);

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

    private static String projectKey(String projectId) {
        return "project:" + projectId;
    }

    private static String ownerListKey(String ownerId) {
        return "projects:owner:" + ownerId;
    }

    public Optional<ProjectResponse> getProject(String projectId) {
        return read(projectKey(projectId), ProjectResponse.class);
    }

    public void putProject(ProjectResponse project) {
        write(projectKey(project.getId()), project);
    }

    public void evictProject(String projectId) {
        keyCommands.del(projectKey(projectId));
    }

    public Optional<List<ProjectSummaryResponse>> getOwnerList(String ownerId) {
        String raw = safeGet(ownerListKey(ownerId));
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(raw, new TypeReference<List<ProjectSummaryResponse>>() { }));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to deserialize cached owner project list for %s", ownerId);
            return Optional.empty();
        }
    }

    public void putOwnerList(String ownerId, List<ProjectSummaryResponse> items) {
        try {
            String json = objectMapper.writeValueAsString(items);
            valueCommands.set(ownerListKey(ownerId), json);
            keyCommands.expire(ownerListKey(ownerId), TTL.toSeconds());
        } catch (JsonProcessingException e) {
            LOG.warnf(e, "Failed to serialize owner project list for %s", ownerId);
        }
    }

    public void evictOwnerList(String ownerId) {
        keyCommands.del(ownerListKey(ownerId));
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
