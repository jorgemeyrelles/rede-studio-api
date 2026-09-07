package br.com.redestudio.repositories;

import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.exceptions.UserAlreadyExistsException;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
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
     * Finds users whose username contains the given substring, case-insensitively.
     *
     * @param partial the substring to search for (treated as a literal, not a regex)
     * @return list of matching users (may be empty)
     */
    public List<UserEntity> findByUsernameContains(String partial) {
        String safePattern = escapeRegex(partial);
        return find("{'username': {$regex: ?1, $options: 'i'}}", safePattern).list();
    }

    /**
     * Escapes regex metacharacters so a user-supplied substring is matched
     * literally. Backslash-escaping is portable between Java's regex engine
     * and MongoDB's (PCRE-based) — unlike {@link java.util.regex.Pattern#quote},
     * whose {@code \Q...\E} delimiters are Java-specific and would be sent to
     * MongoDB as literal characters instead of being interpreted as quoting.
     */
    private static String escapeRegex(String input) {
        return input.replaceAll("([.^$|()\\[\\]{}*+?\\\\])", "\\\\$1");
    }

    /**
     * Persists a new user, translating a unique-index violation into a
     * domain exception.
     *
     * <p>The service layer already pre-checks email/username uniqueness
     * before calling this, but that check-then-act is racy under concurrent
     * requests — this is the actual guarantee, enforced by MongoDB's unique
     * indexes on {@code email}/{@code username} (created by
     * {@code StartupRunner}). Any other write failure propagates unchanged
     * (falls through to the generic 500 in {@code GlobalExceptionHandler}).
     *
     * @param user the entity to persist
     * @throws UserAlreadyExistsException if the write violates the unique
     *                                     email/username index
     */
    public void persistUser(UserEntity user) {
        try {
            persist(user);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw new UserAlreadyExistsException(
                        "Email or username already in use: " + user.getEmail() + " / " + user.getUsername());
            }
            throw e;
        }
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

    /**
     * Deletes the user with the given email address.
     *
     * @param email the email of the user to delete
     * @return the number of documents deleted (0 if not found, 1 otherwise)
     */
    public long deleteByEmail(String email) {
        return delete("email", email);
    }
}
