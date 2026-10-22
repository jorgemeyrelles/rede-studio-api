package br.com.redestudio.messaging;

import br.com.redestudio.dtos.CloudStatePayload;
import br.com.redestudio.dtos.NetworkStatePayload;
import br.com.redestudio.entities.NetworkType;

/**
 * Payload published to the {@code project.create} RabbitMQ queue.
 *
 * <p>{@code projectId} and the state id ({@code networkStateId} or
 * {@code cloudStateId}) are pre-generated in {@code ProjectService} (never
 * left for Mongo to assign) so the Redis cache written synchronously by the
 * request and the document eventually persisted by
 * {@link ProjectMutationConsumer} always agree on the same ids. Exactly one
 * of the two state pairs is non-null, decided by {@code networkType}.
 */
public record ProjectCreateMessage(
        String projectId,
        String networkStateId,
        String ownerId,
        String name,
        NetworkType networkType,
        NetworkStatePayload networkState,
        String cloudStateId,
        CloudStatePayload cloudState) {
}
