package br.com.redestudio.configurations;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Typed configuration binding for JWT settings.
 *
 * <p>Uses {@link ConfigMapping} — the modern Quarkus alternative to scattered
 * {@code @ConfigProperty} fields. All values are sourced from {@code application.yml}
 * under the {@code app.jwt} prefix and are profile-aware (%dev / %master).
 *
 * <p>Consumed by:
 * <ul>
 *   <li>{@code JwtTokenBuilder} — issuer and expiration for token generation</li>
 *   <li>{@code AuthService} — expiration exposed in AuthResponse</li>
 * </ul>
 */
@ConfigMapping(prefix = "app.jwt")
public interface JwtConfiguration {

    /**
     * JWT issuer claim ({@code iss}).
     * Must match {@code mp.jwt.verify.issuer} used by SmallRye JWT verifier.
     *
     * @return issuer URI (e.g. {@code https://redestudio.local})
     */
    String issuer();

    /**
     * Token lifetime in seconds.
     * Default: 86400 (24 h) in dev, 3600 (1 h) in master.
     *
     * @return expiration duration in seconds
     */
    @WithName("expiration-seconds")
    long expirationSeconds();

    /**
     * Filesystem path to the RSA private key PEM file used for signing.
     * Mounted via Docker volume in dev; injected by AWS Secrets Manager in master.
     *
     * @return absolute path to {@code privateKey.pem}
     */
    @WithName("private-key-location")
    String privateKeyLocation();
}
