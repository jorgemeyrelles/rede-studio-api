package br.com.redestudio.messaging;

import br.com.redestudio.dtos.request.EquipmentPriceRequest;

/**
 * Payload published to the {@code equipment.update} RabbitMQ queue.
 *
 * <p>Carries the equipment's full resulting state (not just the changed
 * fields) — {@code EquipmentService#update} already merges the partial
 * {@code UpdateEquipmentRequest} onto the cached/loaded entry before
 * publishing, so this consumer always does a deterministic full-field
 * write, never a partial Mongo update.
 */
public record EquipmentUpdateMessage(
        String equipmentId,
        String brand,
        String model,
        String function,
        EquipmentPriceRequest price) {
}
