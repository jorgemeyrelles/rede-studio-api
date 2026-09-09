package br.com.redestudio.handlers;

import br.com.redestudio.dtos.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.stream.Collectors;

/**
 * Dedicated mapper for bean-validation errors.
 *
 * <p>Quarkus/Hibernate Validator registers its own built-in mapper for
 * {@link ConstraintViolationException} that takes precedence over the generic
 * {@code ExceptionMapper<Exception>} in {@link GlobalExceptionHandler}. This
 * dedicated class overrides that built-in mapper and ensures constraint violations
 * are serialised in our standard {@link ErrorResponse} format.
 *
 * <p>The message lists every violated field and its constraint message, sorted
 * alphabetically and joined with {@code "; "}.
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String path = uriInfo != null ? uriInfo.getPath() : "unknown";

        String message = exception.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of(400, "VALIDATION_ERROR", message, path))
                .build();
    }
}
