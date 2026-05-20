package br.com.redestudio.entities;

import br.com.redestudio.collections.CollectionNames;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Set;

/**
 * MongoDB document representing a registered user.
 * Collection: {@value CollectionNames#USERS}
 *
 * <p>{@code @Data} generates: getters, setters, equals, hashCode and toString.
 * {@code @NoArgsConstructor} provides the explicit no-arg constructor required
 * by the BSON codec for deserialization.
 *
 * <p>Indexes created on startup (see StartupRunner):
 * <ul>
 *   <li>unique index on {@code email}</li>
 *   <li>unique index on {@code username}</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@MongoEntity(collection = CollectionNames.USERS)
public class UserEntity {

    @BsonId
    private ObjectId id;

    @BsonProperty("username")
    private String username;

    @BsonProperty("email")
    private String email;

    @BsonProperty("password_hash")
    private String passwordHash;

    /**
     * Set of role strings assigned to this user (e.g. "USER", "ADMIN").
     * Used as MicroProfile JWT {@code groups} claim.
     */
    @BsonProperty("roles")
    private Set<String> roles;

    @BsonProperty("active")
    private boolean active;

    @BsonProperty("created_at")
    private Instant createdAt;

    @BsonProperty("updated_at")
    private Instant updatedAt;

    /**
     * Factory method for new user creation.
     * Sets {@code active = true} and timestamps to now.
     */
    public static UserEntity create(String username, String email, String passwordHash, Set<String> roles) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRoles(roles);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}

