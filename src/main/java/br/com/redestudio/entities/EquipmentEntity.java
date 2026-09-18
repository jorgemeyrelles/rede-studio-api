package br.com.redestudio.entities;

import br.com.redestudio.collections.CollectionNames;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.Instant;

/**
 * MongoDB document representing a piece of network equipment available for
 * the inventory catalog (router, switch, firewall, access point, etc).
 * Collection: {@value CollectionNames#EQUIPMENTS}
 *
 * <p>Unlike {@link ProjectEntity}, this collection has no owner — every
 * equipment document is a shared catalog entry, readable by any
 * authenticated user and writable only by an {@code ADMIN} (see
 * {@code EquipmentController}).
 */
@Data
@NoArgsConstructor
@MongoEntity(collection = CollectionNames.EQUIPMENTS)
public class EquipmentEntity {

    @BsonId
    private ObjectId id;

    @BsonProperty("brand")
    private String brand;

    @BsonProperty("model")
    private String model;

    /** Category/function of the equipment (e.g. "roteador", "switch", "firewall", "access point"). */
    @BsonProperty("function")
    private String function;

    @BsonProperty("price")
    private EquipmentPrice price;

    @BsonProperty("created_at")
    private Instant createdAt;

    @BsonProperty("updated_at")
    private Instant updatedAt;

    /**
     * Factory method for new equipment creation.
     * Sets timestamps to now.
     */
    public static EquipmentEntity create(String brand, String model, String function, EquipmentPrice price) {
        EquipmentEntity equipment = new EquipmentEntity();
        equipment.setBrand(brand);
        equipment.setModel(model);
        equipment.setFunction(function);
        equipment.setPrice(price);
        equipment.setCreatedAt(Instant.now());
        equipment.setUpdatedAt(Instant.now());
        return equipment;
    }
}
