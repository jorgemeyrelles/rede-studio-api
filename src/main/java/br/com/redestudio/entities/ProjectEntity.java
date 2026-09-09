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
 * MongoDB document representing a network topology project.
 * Collection: {@value CollectionNames#PROJECTS}
 *
 * <p>Relations are by ID, mirroring how collections reference each other:
 * {@code ownerId} points at {@code users._id} (resolved from the JWT
 * {@code uid} claim — see {@code JwtTokenBuilder} — never from a
 * client-supplied field), and {@code networkStateId} points at a
 * {@code network_states} document (see {@link NetworkStateEntity}), always
 * created and deleted together with this project — never orphaned, never
 * shared between projects.
 */
@Data
@NoArgsConstructor
@MongoEntity(collection = CollectionNames.PROJECTS)
public class ProjectEntity {

    @BsonId
    private ObjectId id;

    @BsonProperty("owner_id")
    private ObjectId ownerId;

    @BsonProperty("network_state_id")
    private ObjectId networkStateId;

    @BsonProperty("name")
    private String name;

    @BsonProperty("created_at")
    private Instant createdAt;

    @BsonProperty("updated_at")
    private Instant updatedAt;

    /**
     * Factory method for new project creation.
     * Sets timestamps to now.
     */
    public static ProjectEntity create(ObjectId ownerId, String name, ObjectId networkStateId) {
        ProjectEntity project = new ProjectEntity();
        project.setOwnerId(ownerId);
        project.setName(name);
        project.setNetworkStateId(networkStateId);
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        return project;
    }
}
