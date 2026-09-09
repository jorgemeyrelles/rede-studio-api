package br.com.redestudio;

import br.com.redestudio.components.OAuthTokenVerifier;
import br.com.redestudio.exceptions.OAuthVerificationException;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Test double for {@link OAuthTokenVerifier} — avoids hitting the real
 * Google/Microsoft JWKS endpoints from the test suite. Quarkus automatically
 * activates {@code @Alternative} beans found on the test classpath, no
 * {@code beans.xml}/Mockito needed (this project has neither).
 *
 * <p>Tests encode the desired outcome directly into the fake "idToken":
 * {@code "valid:<providerUserId>:<email>:<name>"} succeeds and returns that
 * identity; anything else fails verification, same as a real bad token would.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeOAuthTokenVerifier extends OAuthTokenVerifier {

    @Override
    public OAuthIdentity verify(String provider, String idToken) {
        if (idToken != null && idToken.startsWith("valid:")) {
            String[] parts = idToken.split(":", 4);
            return new OAuthIdentity(parts[1], parts[2], parts[3]);
        }
        throw new OAuthVerificationException();
    }
}
