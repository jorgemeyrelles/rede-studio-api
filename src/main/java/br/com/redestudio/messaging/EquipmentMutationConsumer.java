package br.com.redestudio.messaging;

import br.com.redestudio.dtos.request.EquipmentPriceRequest;
import br.com.redestudio.entities.EquipmentEntity;
import br.com.redestudio.entities.EquipmentPrice;
import br.com.redestudio.repositories.EquipmentRepository;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Consumes equipment mutation events and performs the actual MongoDB
 * writes — the synchronous request path ({@code EquipmentService}) only
 * updates Redis and publishes here, it never touches Mongo directly.
 *
 * <p>Every message carries the id pre-generated (or already known) by the
 * synchronous path, so persistence here is always a deterministic
 * insert/update by a known {@code _id} — never a "create if not exists"
 * guess.
 */
@ApplicationScoped
public class EquipmentMutationConsumer {

    private static final Logger LOG = Logger.getLogger(EquipmentMutationConsumer.class);

    @Inject
    EquipmentRepository equipmentRepository;

    @Incoming("equipment-create-in")
    public void onCreate(JsonObject payload) {
        EquipmentCreateMessage message = payload.mapTo(EquipmentCreateMessage.class);
        persistNew(message);
    }

    @Incoming("equipment-bulk-create-in")
    public void onBulkCreate(JsonObject payload) {
        EquipmentBulkCreateMessage message = payload.mapTo(EquipmentBulkCreateMessage.class);
        message.items().forEach(this::persistNew);
    }

    @Incoming("equipment-update-in")
    public void onUpdate(JsonObject payload) {
        EquipmentUpdateMessage message = payload.mapTo(EquipmentUpdateMessage.class);

        EquipmentEntity equipment = equipmentRepository.findById(new ObjectId(message.equipmentId()));
        if (equipment == null) {
            LOG.warnf("equipment.update: equipment %s not found (already deleted?)", message.equipmentId());
            return;
        }
        equipment.setBrand(message.brand());
        equipment.setModel(message.model());
        equipment.setFunction(message.function());
        equipment.setPrice(toEntityPrice(message.price()));
        equipment.setUpdatedAt(Instant.now());
        equipmentRepository.update(equipment);
    }

    @Incoming("equipment-delete-in")
    public void onDelete(JsonObject payload) {
        EquipmentDeleteMessage message = payload.mapTo(EquipmentDeleteMessage.class);
        equipmentRepository.deleteById(new ObjectId(message.equipmentId()));
    }

    private void persistNew(EquipmentCreateMessage message) {
        EquipmentEntity equipment = EquipmentEntity.create(
                message.brand(), message.model(), message.function(), toEntityPrice(message.price()));
        equipment.setId(new ObjectId(message.equipmentId()));
        equipmentRepository.persist(equipment);
    }

    private static EquipmentPrice toEntityPrice(EquipmentPriceRequest price) {
        return new EquipmentPrice(price.getApproxPriceUsd(), price.getApproxPriceBrl(), price.getScannedAt());
    }
}
