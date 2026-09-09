package br.com.redestudio.configurations;

import io.smallrye.config.ConfigMapping;

/**
 * Typed configuration for the default admin user provisioned at startup.
 *
 * <p>Values are resolved from environment variables in all profiles.
 * In {@code %dev} sensible defaults are provided so the application starts
 * without manual setup. In {@code %master} all three variables must be
 * supplied explicitly — no defaults are set to avoid shipping credentials.
 *
 * <p>Consumed exclusively by {@code StartupRunner#ensureAdminUser()}.
 */
@ConfigMapping(prefix = "app.admin")
public interface AdminConfiguration {

    /** Admin account e-mail — also used as login credential. */
    String email();

    /** Admin account username (login display name). */
    String username();

    /**
     * Plain-text password used only at first-run provisioning.
     * The value is immediately hashed via BCrypt (cost=12) before
     * persisting — it is never stored in plain text.
     */
    String password();
}
