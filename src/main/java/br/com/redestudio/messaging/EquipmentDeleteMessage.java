package br.com.redestudio.messaging;

/**
 * Payload published to the {@code equipment.delete} RabbitMQ queue.
 */
public record EquipmentDeleteMessage(String equipmentId) {
}
