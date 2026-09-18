package br.com.redestudio.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Publishes equipment mutation events to their respective RabbitMQ queues —
 * one queue per operation (mirrors {@link br.com.redestudio.messaging.ProjectMutationProducer}'s
 * single-purpose-queue style), consolidated into one producer class since
 * all four are the same "equipment write" concern. Consumed asynchronously
 * by {@link EquipmentMutationConsumer}.
 */
@ApplicationScoped
public class EquipmentMutationProducer {

    @Channel("equipment-create-out")
    Emitter<EquipmentCreateMessage> createEmitter;

    @Channel("equipment-bulk-create-out")
    Emitter<EquipmentBulkCreateMessage> bulkCreateEmitter;

    @Channel("equipment-update-out")
    Emitter<EquipmentUpdateMessage> updateEmitter;

    @Channel("equipment-delete-out")
    Emitter<EquipmentDeleteMessage> deleteEmitter;

    public void publishCreate(EquipmentCreateMessage message) {
        createEmitter.send(message);
    }

    public void publishBulkCreate(EquipmentBulkCreateMessage message) {
        bulkCreateEmitter.send(message);
    }

    public void publishUpdate(EquipmentUpdateMessage message) {
        updateEmitter.send(message);
    }

    public void publishDelete(EquipmentDeleteMessage message) {
        deleteEmitter.send(message);
    }
}
