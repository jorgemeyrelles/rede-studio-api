package br.com.redestudio.exceptions;

/**
 * Thrown when a write targets the wrong state format for a project's
 * {@code networkType} — e.g. an on-prem snapshot sent to a {@code CLOUD}
 * project, or a cloud snapshot to an on-prem one.
 *
 * <p>Mapped to HTTP 409 Conflict by {@code GlobalExceptionHandler}.
 */
public class ProjectTypeMismatchException extends RuntimeException {

    public ProjectTypeMismatchException(String message) {
        super(message);
    }
}
