package br.com.redestudio.messaging;

import br.com.redestudio.dtos.request.EquipmentPriceRequest;

import java.util.List;

/**
 * Payload published to the {@code equipment.create} RabbitMQ queue.
 *
 * <p>{@code equipmentId} is pre-generated in {@code EquipmentService} (never
 * left for Mongo to assign) so the Redis cache written synchronously by the
 * request and the document eventually persisted by
 * {@link EquipmentMutationConsumer} always agree on the same id.
 */
public record EquipmentCreateMessage(
        String equipmentId,
        String brand,
        String model,
        List<String> function,
        EquipmentPriceRequest price) {
}
