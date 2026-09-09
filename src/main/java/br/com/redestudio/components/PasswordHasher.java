package br.com.redestudio.components;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Component responsible for hashing and verifying passwords using BCrypt.
 *
 * <p>Delegates to Quarkus {@link BcryptUtil} which wraps the WildFly Elytron
 * BCrypt implementation. No additional configuration required.
 *
 * <p>BCrypt work factor is set to 12 — a balance between security and
 * acceptable latency (~300ms on modern hardware).
 */
@ApplicationScoped
public class PasswordHasher {

    private static final int BCRYPT_COST = 12;

    /**
     * Hashes a plain-text password using BCrypt.
     *
     * @param rawPassword the plain-text password supplied by the user
     * @return the BCrypt hash string to be stored in the database
     */
    public String hash(String rawPassword) {
        return BcryptUtil.bcryptHash(rawPassword, BCRYPT_COST);
    }

    /**
     * Verifies a plain-text password against a stored BCrypt hash.
     *
     * @param rawPassword    the plain-text password to check
     * @param hashedPassword the BCrypt hash stored in the database
     * @return {@code true} if the password matches the hash, {@code false} otherwise
     */
    public boolean verify(String rawPassword, String hashedPassword) {
        return BcryptUtil.matches(rawPassword, hashedPassword);
    }
}
