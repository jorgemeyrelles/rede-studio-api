package br.com.redestudio.configurations;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.List;

/**
 * Typed configuration binding for HTTP CORS settings.
 *
 * <p>Values are sourced from {@code application.yml} under the
 * {@code quarkus.http.cors} prefix and are profile-aware (%dev / %master).
 *
 * <p>In dev, origins include {@code http://localhost:5173} (Vite frontend).
 * In master, origins are restricted to the production domain via {@code CORS_ORIGINS}
 * environment variable injected at runtime.
 *
 * <p>Note: Quarkus handles CORS automatically via its built-in filter when
 * {@code quarkus.http.cors=true}. This class exposes the resolved values
 * for use in OpenAPI documentation and for explicit inspection in tests.
 */
@ConfigMapping(prefix = "quarkus.http.cors")
public interface CorsConfiguration {

    /**
     * Comma-separated list of allowed origins.
     *
     * @return allowed origins string (e.g. {@code http://localhost:5173})
     */
    String origins();

    /**
     * Comma-separated list of allowed HTTP methods.
     *
     * @return allowed methods string (e.g. {@code GET,POST,PUT,PATCH,DELETE,OPTIONS})
     */
    String methods();

    /**
     * Comma-separated list of allowed request headers.
     *
     * @return allowed headers string
     */
    String headers();

    /**
     * Comma-separated list of response headers exposed to the browser.
     *
     * @return exposed headers string
     */
    @WithName("exposed-headers")
    String exposedHeaders();
}
