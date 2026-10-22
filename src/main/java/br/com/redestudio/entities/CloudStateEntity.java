package br.com.redestudio.entities;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.dtos.CloudStatePayload;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document holding one {@code CLOUD} project's architecture draft.
 * Collection: {@value CollectionNames#CLOUD_STATES}
 *
 * <p>1:1 with a {@link ProjectEntity} (via {@code ProjectEntity.cloudStateId})
 * — the cloud counterpart of {@link NetworkStateEntity}, kept in its own
 * collection so neither {@code $jsonSchema} validator has to be loosened
 * to accept the other format. Created and deleted together with the
 * project. Fields are {@link Document} for the same codec reason explained
 * on {@link NetworkStateEntity}.
 */
@Data
@NoArgsConstructor
@MongoEntity(collection = CollectionNames.CLOUD_STATES)
public class CloudStateEntity {

    @BsonId
    private ObjectId id;

    @BsonProperty("schema_version")
    private Integer schemaVersion;

    @BsonProperty("provider")
    private String provider;

    @BsonProperty("resources")
    private List<Document> resources;

    @BsonProperty("connections")
    private List<Document> connections;

    @BsonProperty("assumptions")
    private Document assumptions;

    @BsonProperty("ui")
    private Document ui;

    @BsonProperty("meta")
    private Document meta;

    /** Builds a new entity from the API payload — used on create and on snapshot self-heal. */
    public static CloudStateEntity fromPayload(CloudStatePayload payload) {
        CloudStateEntity entity = new CloudStateEntity();
        entity.applyPayload(payload);
        return entity;
    }

    /** Applies a new payload onto this existing entity (in place) — used on cloud snapshot save. */
    public void applyPayload(CloudStatePayload payload) {
        setSchemaVersion(payload.getSchemaVersion());
        setProvider(payload.getProvider());
        setResources(toDocumentList(payload.getResources()));
        setConnections(toDocumentList(payload.getConnections()));
        setAssumptions(new Document(payload.getAssumptions()));
        setUi(new Document(payload.getUi()));
        setMeta(new Document(payload.getMeta()));
    }

    /** Maps this entity back to the API payload shape (get project response). */
    public CloudStatePayload toPayload() {
        return new CloudStatePayload(
                schemaVersion, provider,
                new ArrayList<>(resources), new ArrayList<>(connections),
                assumptions, ui, meta);
    }

    /** Same top-level wrapping as {@code NetworkStateEntity#toDocumentList}. */
    @SuppressWarnings("unchecked")
    private static List<Document> toDocumentList(List<Object> items) {
        List<Document> result = new ArrayList<>(items.size());
        for (Object item : items) {
            result.add(item instanceof Map ? new Document((Map<String, Object>) item) : new Document());
        }
        return result;
    }
}
