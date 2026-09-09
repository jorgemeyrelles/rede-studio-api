package br.com.redestudio;

import br.com.redestudio.dtos.request.RegisterRequest;
import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.exceptions.OAuthVerificationException;
import br.com.redestudio.repositories.UserRepository;
import br.com.redestudio.services.AuthService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link AuthService#loginOrRegisterOAuth} — exercises
 * the service layer directly, hitting a real MongoDB instance (Quarkus Dev
 * Services). {@link FakeOAuthTokenVerifier} stands in for the real
 * Google/Microsoft JWKS verification (see its javadoc for the fake token format).
 *
 * <p>Each test uses a unique email/provider-id to avoid collision with other
 * test classes sharing the same application instance (and database).
 *
 * <p>Account creation (both password and OAuth) is asynchronous — the Redis
 * → fila → BD path documented on {@link AuthService#register} — so a test
 * that needs the document to already exist (to log in again, or to link a
 * second identity onto it) polls for it via {@link #waitFor} instead of
 * asserting immediately after the call returns.
 */
@QuarkusTest
class AuthServiceOAuthTest {

    @Inject
    AuthService authService;

    @Inject
    UserRepository userRepository;

    @Test
    void loginOrRegisterOAuth_newUser_createsAccountLinkedToProvider() {
        var response = authService.loginOrRegisterOAuth(
                "google", "valid:sub-new-1:oauth-new@test.local:New User");

        assertNotNull(response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertTrue(response.getRoles().contains("USER"));

        var saved = waitFor(() -> userRepository.findByEmail("oauth-new@test.local"));
        assertNull(saved.getPasswordHash(), "OAuth-only account must not have a password hash");
        assertEquals("google", saved.getOauthProvider());
        assertEquals("sub-new-1", saved.getOauthProviderId());
        assertTrue(saved.isActive());
    }

    @Test
    void loginOrRegisterOAuth_repeatedLogin_reusesTheSameAccount() {
        var first = authService.loginOrRegisterOAuth(
                "google", "valid:sub-repeat-1:oauth-repeat@test.local:Repeat User");
        // Wait for the async consumer to land the first signup before logging
        // in again — a genuinely-immediate repeat is register()'s territory
        // (duplicate-email conflict), not login's ("welcome back") territory.
        waitFor(() -> userRepository.findByOauthProviderId("google", "sub-repeat-1"));

        var second = authService.loginOrRegisterOAuth(
                "google", "valid:sub-repeat-1:oauth-repeat@test.local:Repeat User");

        assertNotNull(second.getToken());
        assertEquals(first.getId(), second.getId(), "Second login must resolve to the same user id");
    }

    @Test
    void loginOrRegisterOAuth_emailMatchesExistingPasswordAccount_linksInstead() {
        authService.register(new RegisterRequest("linkeduser", "link-me@test.local", "Password@Test1"));
        waitFor(() -> userRepository.findByEmail("link-me@test.local"));

        var response = authService.loginOrRegisterOAuth(
                "microsoft", "valid:sub-link-1:link-me@test.local:Link Me");

        assertNotNull(response.getToken());
        var saved = waitFor(() -> userRepository.findByOauthProviderId("microsoft", "sub-link-1"));
        assertEquals("link-me@test.local", saved.getEmail());
        assertNotNull(saved.getPasswordHash(), "linking must not wipe the existing password hash");
    }

    @Test
    void loginOrRegisterOAuth_invalidToken_throwsOAuthVerificationException() {
        assertThrows(OAuthVerificationException.class,
                () -> authService.loginOrRegisterOAuth("google", "not-a-real-token"));
    }

    @Test
    void loginOrRegisterOAuth_unsupportedProvider_throwsOAuthVerificationException() {
        assertThrows(OAuthVerificationException.class,
                () -> authService.loginOrRegisterOAuth("facebook", "valid:sub-x:x@test.local:X"));
    }

    /** Polls {@code lookup} until it resolves, up to 3s — see class javadoc. */
    private static UserEntity waitFor(Supplier<Optional<UserEntity>> lookup) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            Optional<UserEntity> found = lookup.get();
            if (found.isPresent()) {
                return found.get();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return fail("User document was not persisted within 3s of the async registration path");
    }
}
