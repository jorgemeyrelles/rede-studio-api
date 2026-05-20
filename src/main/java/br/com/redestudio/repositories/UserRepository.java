package br.com.redestudio.repositories;

import br.com.redestudio.entities.UserEntity;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Repository for {@link UserEntity} documents in MongoDB.
 *
 * <p>Extends {@link PanacheMongoRepository} which provides out-of-the-box:
 * {@code find}, {@code findAll}, {@code list}, {@code count},
 * {@code persist}, {@code update}, {@code delete} and more.
 *
 * <p>Custom query methods follow Panache's field-based query DSL.
 * Field names in queries must match the BSON document field names
 * (as declared via {@code @BsonProperty} in the entity).
 */
@ApplicationScoped
public class UserRepository implements PanacheMongoRepository<UserEntity> {

    /**
     * Finds a user by their email address.
     *
     * @param email the email to search for
     * @return an {@link Optional} containing the user, or empty if not found
     */
    public Optional<UserEntity> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    /**
     * Finds a user by their username.
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the user, or empty if not found
     */
    public Optional<UserEntity> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }

    /**
     * Checks whether a user with the given email already exists.
     *
     * @param email the email to check
     * @return {@code true} if at least one document with that email exists
     */
    public boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }

    /**
     * Checks whether a user with the given username already exists.
     *
     * @param username the username to check
     * @return {@code true} if at least one document with that username exists
     */
    public boolean existsByUsername(String username) {
        return count("username", username) > 0;
    }
}
