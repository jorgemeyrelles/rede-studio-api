package br.com.redestudio.messaging;

import java.util.List;

/**
 * Payload published to the {@code equipment.bulk-create} RabbitMQ queue —
 * a batch of {@link EquipmentCreateMessage}-shaped entries, each already
 * carrying its own pre-generated id (same reasoning as a single create).
 */
public record EquipmentBulkCreateMessage(List<EquipmentCreateMessage> items) {
}
