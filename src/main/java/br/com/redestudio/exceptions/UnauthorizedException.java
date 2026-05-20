package br.com.redestudio.exceptions;

/**
 * Thrown when a request reaches a protected resource without a valid JWT,
 * or when the token does not carry the required roles.
 *
 * <p>Mapped to HTTP 401 Unauthorized by {@code GlobalExceptionHandler}.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
