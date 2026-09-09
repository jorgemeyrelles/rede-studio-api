package br.com.redestudio.exceptions;

/**
 * Thrown when login credentials (email / password) do not match
 * any active user in the database.
 *
 * <p>Mapped to HTTP 401 Unauthorized by {@code GlobalExceptionHandler}.
 * The message is intentionally generic to avoid user enumeration.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
