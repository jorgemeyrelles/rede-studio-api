package br.com.redestudio.handlers;

import br.com.redestudio.dtos.response.ErrorResponse;
import br.com.redestudio.exceptions.InvalidCredentialsException;
import br.com.redestudio.exceptions.UnauthorizedException;
import br.com.redestudio.exceptions.UserAlreadyExistsException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Centralised JAX-RS exception mapper.
 *
 * <p>Catches all exceptions thrown by the application and maps them to a
 * standardised {@link ErrorResponse} JSON body. Prevents stack traces from
 * leaking to API clients and ensures every error shares the same structure.
 *
 * <p>Mapped exceptions:
 * <ul>
 *   <li>{@link UserAlreadyExistsException} → 409 Conflict</li>
 *   <li>{@link InvalidCredentialsException} → 401 Unauthorized</li>
 *   <li>{@link UnauthorizedException} → 401 Unauthorized</li>
 *   <li>{@link ConstraintViolationException} → 400 Bad Request (validation)</li>
 *   <li>{@link NotFoundException} → 404 Not Found</li>
 *   <li>{@link NotAllowedException} → 405 Method Not Allowed</li>
 *   <li>{@link Exception} → 500 Internal Server Error (catch-all)</li>
 * </ul>
 */
@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception exception) {
        String path = uriInfo != null ? uriInfo.getPath() : "unknown";

        if (exception instanceof UserAlreadyExistsException ex) {
            return build(Response.Status.CONFLICT, "CONFLICT", ex.getMessage(), path);
        }

        if (exception instanceof InvalidCredentialsException ex) {
            return build(Response.Status.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), path);
        }

        if (exception instanceof UnauthorizedException ex) {
            return build(Response.Status.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), path);
        }

        if (exception instanceof NotFoundException) {
            return build(Response.Status.NOT_FOUND, "NOT_FOUND", "Resource not found", path);
        }

        if (exception instanceof NotAllowedException) {
            return build(
                    Response.Status.METHOD_NOT_ALLOWED,
                    "METHOD_NOT_ALLOWED",
                    "HTTP method not allowed",
                    path);
        }

        LOG.errorf(exception, "Unhandled exception on path [%s]", path);
        return build(
                Response.Status.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                path);
    }

    private Response build(Response.Status status, String error, String message, String path) {
        ErrorResponse body = ErrorResponse.of(status.getStatusCode(), error, message, path);
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
