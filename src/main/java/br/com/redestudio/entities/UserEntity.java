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

    /** Null for accounts created via OAuth (no local password). */
    @BsonProperty("password_hash")
    private String passwordHash;

    /**
     * Social login provider that authenticated this account ({@code "google"}
     * or {@code "microsoft"}), or {@code null} for a password-only account.
     * Always set together with {@link #oauthProviderId}.
     */
    @BsonProperty("oauth_provider")
    private String oauthProvider;

    /**
     * The provider's own unique subject id for this user (JWT {@code sub}
     * claim of the verified ID token) — stable even if the user's email
     * changes on the provider side. {@code null} for a password-only account.
     */
    @BsonProperty("oauth_provider_id")
    private String oauthProviderId;

    /**
     * Set of role strings assigned to this user (e.g. "USER", "ADMIN").
     * Used as MicroProfile JWT {@code groups} claim.
     */
    @BsonProperty("roles")
    private Set<String> roles;

    @BsonProperty("active")
    private boolean active;

    /**
     * ISO 639-1 language code used by the frontend chrome UI (e.g. "pt",
     * "en", "es"). Defaults to "pt" for every new account — see
     * {@link #create}. Self-service editable via {@code PATCH /api/users/me}.
     */
    @BsonProperty("preferred_language")
    private String preferredLanguage;

    @BsonProperty("created_at")
    private Instant createdAt;

    @BsonProperty("updated_at")
    private Instant updatedAt;

    /**
     * Factory method for new user creation.
     * Sets {@code active = true} and timestamps to now.
     *
     * @param passwordHash    BCrypt hash, or {@code null} for an OAuth-only account
     * @param oauthProvider   {@code "google"}/{@code "microsoft"}, or {@code null} for a password account
     * @param oauthProviderId the provider's subject id, or {@code null} for a password account
     */
    public static UserEntity create(
            String username,
            String email,
            String passwordHash,
            Set<String> roles,
            String oauthProvider,
            String oauthProviderId) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRoles(roles);
        user.setActive(true);
        user.setPreferredLanguage("pt");
        user.setOauthProvider(oauthProvider);
        user.setOauthProviderId(oauthProviderId);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    /** Password-account convenience overload — equivalent to passing {@code null, null} for the OAuth fields. */
    public static UserEntity create(String username, String email, String passwordHash, Set<String> roles) {
        return create(username, email, passwordHash, roles, null, null);
    }
}

