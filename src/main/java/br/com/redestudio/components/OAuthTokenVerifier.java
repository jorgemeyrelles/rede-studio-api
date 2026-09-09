package br.com.redestudio.components;

import br.com.redestudio.configurations.OAuthConfiguration;
import br.com.redestudio.exceptions.OAuthVerificationException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Verifies Google/Microsoft OAuth ID tokens against the provider's public
 * JWKS — signature, issuer, audience and expiration — without ever talking
 * to the provider itself. The frontend's SDK already did the interactive
 * sign-in and handed us the resulting ID token; this only confirms it's
 * genuine and was issued for our client ID. No client secret is involved.
 *
 * <p>One {@link ConfigurableJWTProcessor} is built per provider and cached
 * for the life of the application — {@link RemoteJWKSet} already caches the
 * fetched keys internally, so this avoids re-fetching the JWKS on every
 * single login.
 */
@ApplicationScoped
public class OAuthTokenVerifier {

    private static final String GOOGLE = "google";
    private static final String MICROSOFT = "microsoft";

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String MICROSOFT_JWKS_URL =
            "https://login.microsoftonline.com/common/discovery/v2.0/keys";

    private static final Pattern GOOGLE_ISSUER = Pattern.compile("^(https://)?accounts\\.google\\.com$");

    // The "common" multi-tenant endpoint issues tokens whose issuer embeds
    // the actual tenant id (only known once the token arrives) — an exact
    // string match isn't possible here, only the shape of the URL is fixed.
    private static final Pattern MICROSOFT_ISSUER =
            Pattern.compile("^https://login\\.microsoftonline\\.com/[^/]+/v2\\.0$");

    @Inject
    OAuthConfiguration oAuthConfiguration;

    private final Map<String, ConfigurableJWTProcessor<SecurityContext>> processors = new ConcurrentHashMap<>();

    /**
     * Verifies {@code idToken} was issued by {@code provider} for our
     * client ID and hasn't expired, then extracts the caller's identity.
     *
     * @param provider {@code "google"} or {@code "microsoft"} (case-insensitive)
     * @param idToken  the ID token JWT obtained by the frontend's SDK
     * @return the verified identity (provider subject id, email, display name)
     * @throws OAuthVerificationException if the provider is unsupported, or
     *                                     the token is malformed, unsigned by
     *                                     a trusted key, expired, or issued
     *                                     for a different audience/issuer
     */
    public OAuthIdentity verify(String provider, String idToken) {
        String normalizedProvider = provider == null ? "" : provider.toLowerCase();
        if (!GOOGLE.equals(normalizedProvider) && !MICROSOFT.equals(normalizedProvider)) {
            throw new OAuthVerificationException();
        }

        try {
            JWTClaimsSet claims = processorFor(normalizedProvider).process(idToken, null);

            String issuer = claims.getIssuer();
            if (issuer == null || !issuerPattern(normalizedProvider).matcher(issuer).matches()) {
                throw new OAuthVerificationException();
            }

            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(clientIdFor(normalizedProvider))) {
                throw new OAuthVerificationException();
            }

            String subject = claims.getSubject();
            // Microsoft doesn't always populate "email" (tenant-dependent) —
            // "preferred_username" is the documented fallback for that case.
            String email = claims.getStringClaim("email");
            if (email == null) {
                email = claims.getStringClaim("preferred_username");
            }
            if (subject == null || email == null) {
                throw new OAuthVerificationException();
            }

            String name = claims.getStringClaim("name");
            return new OAuthIdentity(subject, email, name != null ? name : email);
        } catch (OAuthVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthVerificationException();
        }
    }

    private ConfigurableJWTProcessor<SecurityContext> processorFor(String provider) {
        return processors.computeIfAbsent(provider, this::buildProcessor);
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor(String provider) {
        String jwksUrl = GOOGLE.equals(provider) ? GOOGLE_JWKS_URL : MICROSOFT_JWKS_URL;

        try {
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(URI.create(jwksUrl).toURL());
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
            processor.setJWTClaimsSetVerifier(
                    new DefaultJWTClaimsVerifier<>(null, Set.of("iss", "sub", "aud", "exp")));
            return processor;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid JWKS URL for provider " + provider, e);
        }
    }

    private Pattern issuerPattern(String provider) {
        return GOOGLE.equals(provider) ? GOOGLE_ISSUER : MICROSOFT_ISSUER;
    }

    private String clientIdFor(String provider) {
        return (GOOGLE.equals(provider) ? oAuthConfiguration.googleClientId() : oAuthConfiguration.microsoftClientId())
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "OAuth client id not configured for provider '" + provider
                                + "' — set GOOGLE_OAUTH_CLIENT_ID/MICROSOFT_OAUTH_CLIENT_ID"));
    }

    /** Normalized identity extracted from a verified OAuth ID token. */
    public record OAuthIdentity(String providerUserId, String email, String name) {
    }
}
