package br.com.redestudio.services;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

/**
 * Redis-backed idempotency guard used to deduplicate write requests before
 * they are handed off to a queue for asynchronous persistence — the
 * "LB → Redis → fila → BD" pattern.
 *
 * <p>{@link #claim} is a fast, best-effort dedup check with a TTL (not a
 * permanent uniqueness constraint): the durable guarantee still lives in
 * MongoDB's unique indexes, re-validated by the queue consumer. If the
 * process crashes between {@code SETNX} and {@code EXPIRE}, the claimed key
 * never expires on its own — an accepted trade-off for a request-dedup
 * guard whose correctness does not depend on the key eventually clearing.
 */
@ApplicationScoped
public class IdempotencyService {

    @Inject
    RedisDataSource redisDataSource;

    private ValueCommands<String, String> valueCommands;
    private KeyCommands<String> keyCommands;

    @PostConstruct
    void init() {
        valueCommands = redisDataSource.value(String.class);
        keyCommands = redisDataSource.key();
    }

    /**
     * Attempts to claim {@code key} for {@code ttl}.
     *
     * @return {@code true} if this call claimed the key (no prior owner),
     *         {@code false} if another request already holds it
     */
    public boolean claim(String key, Duration ttl) {
        boolean claimed = valueCommands.setnx(key, "1");
        if (claimed) {
            keyCommands.expire(key, ttl.toSeconds());
        }
        return claimed;
    }

    /**
     * Releases a previously claimed key — used to roll back a claim when a
     * later step in the same request fails (e.g. username taken after email
     * was already claimed).
     */
    public void release(String key) {
        keyCommands.del(key);
    }
}
