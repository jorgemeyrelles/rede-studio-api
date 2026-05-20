package br.com.redestudio.services;

import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.exceptions.UserAlreadyExistsException;
import br.com.redestudio.repositories.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for user lifecycle operations.
 *
 * <p>Handles persistence concerns only — authentication logic lives in
 * {@link AuthService}. All methods operate on {@link UserEntity} and
 * delegate storage to {@link UserRepository}.
 */
@ApplicationScoped
public class UserService {

    @Inject
    UserRepository userRepository;

    /**
     * Persists a new user document in MongoDB.
     *
     * <p>Uniqueness of {@code email} and {@code username} must be guaranteed
     * by the caller ({@link AuthService}) before invoking this method.
     * A unique index on both fields also enforces this at the database level
     * (created by {@code StartupRunner}).
     *
     * @param user fully populated {@link UserEntity} to persist
     * @return the persisted entity (id populated by MongoDB)
     */
    public UserEntity createUser(UserEntity user) {
        userRepository.persist(user);
        return user;
    }

    /**
     * Returns all user documents from MongoDB.
     *
     * @return list of all {@link UserEntity} instances (may be empty)
     */
    public List<UserEntity> listAll() {
        return userRepository.listAll();
    }

    /**
     * Looks up a user by username.
     *
     * @param username the username to search for
     * @return an {@link Optional} with the user, or empty if not found
     */
    public Optional<UserEntity> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Looks up a user by email address.
     *
     * @param email the email to search for
     * @return an {@link Optional} with the user, or empty if not found
     */
    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Checks whether an account with the given email already exists.
     *
     * @param email the email to check
     * @return {@code true} if at least one document with that email exists
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Checks whether an account with the given username already exists.
     *
     * @param username the username to check
     * @return {@code true} if at least one document with that username exists
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Updates the {@code updatedAt} timestamp on an existing user and persists
     * the change. Used after profile or credential modifications.
     *
     * @param user the entity to update (must already have a valid {@code id})
     * @return the updated entity
     */
    public UserEntity updateUser(UserEntity user) {
        user.setUpdatedAt(Instant.now());
        userRepository.update(user);
        return user;
    }

    /**
     * Changes the password of the user identified by email.
     *
     * @param email           the email of the user whose password will be changed
     * @param newPasswordHash the BCrypt hash of the new password
     * @return the updated entity
     * @throws NotFoundException if no user with that email exists
     */
    public UserEntity changePassword(String email, String newPasswordHash) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
        user.setPasswordHash(newPasswordHash);
        user.setUpdatedAt(Instant.now());
        userRepository.update(user);
        return user;
    }

    /**
     * Deletes the user identified by email.
     *
     * @param email the email of the user to delete
     * @throws NotFoundException if no user with that email exists
     */
    public void deleteByEmail(String email) {
        long deleted = userRepository.deleteByEmail(email);
        if (deleted == 0) {
            throw new NotFoundException("User not found with email: " + email);
        }
    }
}
