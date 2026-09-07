package br.com.redestudio.services;

import br.com.redestudio.dtos.request.UpdateUserRequest;
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
     * <p>Uniqueness of {@code email} and {@code username} is normally
     * pre-checked by the caller ({@link AuthService}), but that check-then-act
     * is racy under concurrent requests — {@link UserRepository#persistUser}
     * enforces the real guarantee via MongoDB's unique index and throws
     * {@link UserAlreadyExistsException} if it's violated.
     *
     * @param user fully populated {@link UserEntity} to persist
     * @return the persisted entity (id populated by MongoDB)
     * @throws UserAlreadyExistsException if email/username collided with an
     *                                     existing account (race condition)
     */
    public UserEntity createUser(UserEntity user) {
        userRepository.persistUser(user);
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
     * Looks up users whose username contains the given substring, case-insensitively.
     *
     * @param partial the substring to search for
     * @return list of matching users (may be empty)
     */
    public List<UserEntity> searchByNameContains(String partial) {
        return userRepository.findByUsernameContains(partial);
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
     * Partially updates a user's profile fields.
     *
     * <p>Only non-null fields in {@code request} are applied. Username/email
     * uniqueness is re-checked only when that field is actually changing.
     * Password is never touched here — use {@link #changePassword} instead.
     *
     * @param email   the current email of the user to update
     * @param request the fields to change (all optional)
     * @return the updated entity
     * @throws NotFoundException          if no user with that email exists
     * @throws UserAlreadyExistsException if the new username/email is already taken by another account
     */
    public UserEntity patchUser(String email, UpdateUserRequest request) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));

        String newUsername = request.getUsername();
        if (newUsername != null && !newUsername.equals(user.getUsername())) {
            if (userRepository.existsByUsername(newUsername)) {
                throw new UserAlreadyExistsException("Username already in use: " + newUsername);
            }
            user.setUsername(newUsername);
        }

        String newEmail = request.getEmail();
        if (newEmail != null && !newEmail.equals(user.getEmail())) {
            if (userRepository.existsByEmail(newEmail)) {
                throw new UserAlreadyExistsException("Email already in use: " + newEmail);
            }
            user.setEmail(newEmail);
        }

        if (request.getRoles() != null) {
            user.setRoles(request.getRoles());
        }

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

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
