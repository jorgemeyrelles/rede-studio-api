package br.com.redestudio.exceptions;

/**
 * Thrown when a registration attempt uses an email or username
 * that is already associated with an existing account.
 *
 * <p>Mapped to HTTP 409 Conflict by {@code GlobalExceptionHandler}.
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
