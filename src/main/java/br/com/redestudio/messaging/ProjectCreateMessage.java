package br.com.redestudio.messaging;

import br.com.redestudio.dtos.NetworkStatePayload;

/**
 * Payload published to the {@code project.create} RabbitMQ queue.
 *
 * <p>{@code projectId}/{@code networkStateId} are pre-generated in
 * {@code ProjectService} (never left for Mongo to assign) so the Redis
 * cache written synchronously by the request and the document eventually
 * persisted by {@link ProjectMutationConsumer} always agree on the same ids.
 */
public record ProjectCreateMessage(
        String projectId,
        String networkStateId,
        String ownerId,
        String name,
        NetworkStatePayload networkState) {
}
