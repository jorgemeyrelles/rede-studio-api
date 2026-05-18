package br.com.redestudio.entities;

import br.com.redestudio.collections.CollectionNames;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Set;

/**
 * MongoDB document representing a registered user.
 * Collection: {@value CollectionNames#USERS}
 *
 * <p>Indexes created on startup (see StartupRunner):
 * <ul>
 *   <li>unique index on {@code email}</li>
 *   <li>unique index on {@code username}</li>
 * </ul>
 */
@MongoEntity(collection = CollectionNames.USERS)
public class UserEntity {

    @BsonId
    public ObjectId id;

    @BsonProperty("username")
    public String username;

    @BsonProperty("email")
    public String email;

    @BsonProperty("password_hash")
    public String passwordHash;

    /**
     * Set of role strings assigned to this user (e.g. "USER", "ADMIN").
     * Used as MicroProfile JWT {@code groups} claim.
     */
    @BsonProperty("roles")
    public Set<String> roles;

    @BsonProperty("active")
    public boolean active;

    @BsonProperty("created_at")
    public Instant createdAt;

    @BsonProperty("updated_at")
    public Instant updatedAt;

    /**
     * Required no-arg constructor for Panache / BSON codec.
     */
    public UserEntity() {
    }

    /**
     * Factory method for new user creation.
     * Sets {@code active = true} and timestamps to now.
     */
    public static UserEntity create(String username, String email, String passwordHash, Set<String> roles) {
        UserEntity user = new UserEntity();
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHash;
        user.roles = roles;
        user.active = true;
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        return user;
    }
}
