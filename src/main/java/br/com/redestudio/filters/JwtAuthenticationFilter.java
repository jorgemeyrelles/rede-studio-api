package br.com.redestudio.filters;

import br.com.redestudio.dtos.response.ErrorResponse;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Set;

/**
 * JAX-RS request filter that enforces JWT authentication on protected routes.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Skip OPTIONS (CORS preflight) and all public paths.</li>
 *   <li>For every other path: verify that an {@code Authorization: Bearer}
 *       header is present and correctly formatted.</li>
 *   <li>Abort with HTTP 401 and a standard {@link ErrorResponse} body if
 *       the header is missing or malformed — before the request reaches the
 *       endpoint or the SmallRye JWT verifier.</li>
 *   <li>If the header is well-formed, pass the request through. SmallRye JWT
 *       then verifies the token signature and expiry when the endpoint's
 *       {@code @Authenticated} / {@code @RolesAllowed} annotation is evaluated.</li>
 * </ol>
 *
 * <h3>Why {@code proactive=false}</h3>
 * <p>With the default {@code quarkus.http.auth.proactive=true} SmallRye JWT
 * would intercept the request at the Vert.x layer and return its own 401
 * before this filter runs, bypassing our standard {@link ErrorResponse} format.
 * Setting {@code proactive=false} (in {@code application.yml}) makes
 * authentication lazy — triggered only when a secured endpoint is reached —
 * which allows this filter to run first and control the error response.
 *
 * <h3>Public path whitelist</h3>
 * <p>Any path whose prefix is in {@link #PUBLIC_PATH_PREFIXES} is allowed
 * through without a token. Add new public paths here as the API grows.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class JwtAuthenticationFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Path prefixes that do not require a JWT.
     * Checked via {@link String#startsWith(String)} against the request path.
     */
    private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/oauth",
            "/q/health",
            "/q/metrics",
            "/q/openapi",
            "/q/swagger-ui"
    );

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Always allow CORS preflight
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();

        if (isPublicPath(path)) {
            return;
        }

        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.isBlank()) {
            LOG.warnf("Missing Authorization header on protected path [%s]", path);
            abortUnauthorized(requestContext, path, "Missing Authorization header");
            return;
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            LOG.warnf("Malformed Authorization header on path [%s]", path);
            abortUnauthorized(
                    requestContext,
                    path,
                    "Invalid Authorization header format. Expected: Bearer {token}");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).strip();
        if (token.isBlank()) {
            LOG.warnf("Empty Bearer token on path [%s]", path);
            abortUnauthorized(requestContext, path, "JWT token must not be empty");
            return;
        }

        // Token is present and correctly formatted.
        // SmallRye JWT will verify signature and expiry when the endpoint's
        // @Authenticated / @RolesAllowed annotation is evaluated.
        LOG.debugf("Bearer token present for path [%s] — delegating to SmallRye JWT", path);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isPublicPath(String path) {
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void abortUnauthorized(
            ContainerRequestContext ctx, String path, String message) {
        ErrorResponse body = ErrorResponse.of(
                Response.Status.UNAUTHORIZED.getStatusCode(),
                "UNAUTHORIZED",
                message,
                "/" + path);

        ctx.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(body)
                        .build());
    }
}
