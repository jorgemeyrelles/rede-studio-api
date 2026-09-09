package br.com.redestudio.configurations;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Typed configuration for OAuth social login (Google / Microsoft).
 *
 * <p>Both client IDs are public by nature — they travel inside the ID token
 * and the frontend's HTML — so they live in {@code application.yml}, not the
 * Vault. They are used only to validate the {@code aud} claim of the ID
 * token presented by the frontend; no client secret is needed for this flow
 * (see {@code OAuthTokenVerifier}).
 *
 * <p>{@link Optional} (not a plain {@code String} with an empty default) —
 * these are genuinely unset until an operator configures a real provider
 * app, and SmallRye Config rejects an empty string as a value for a
 * required {@code String} property at boot.
 */
@ConfigMapping(prefix = "app.oauth")
public interface OAuthConfiguration {

    @WithName("google-client-id")
    Optional<String> googleClientId();

    @WithName("microsoft-client-id")
    Optional<String> microsoftClientId();
}
