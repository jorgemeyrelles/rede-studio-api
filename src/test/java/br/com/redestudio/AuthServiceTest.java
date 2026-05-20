package br.com.redestudio;

import br.com.redestudio.dtos.request.LoginRequest;
import br.com.redestudio.dtos.request.RegisterRequest;
import br.com.redestudio.exceptions.InvalidCredentialsException;
import br.com.redestudio.exceptions.UserAlreadyExistsException;
import br.com.redestudio.repositories.UserRepository;
import br.com.redestudio.services.AuthService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link AuthService} — exercises the service layer
 * directly, hitting a real MongoDB instance provided by Quarkus Dev Services.
 *
 * <p>Each test uses a unique email to avoid collision with {@code AuthControllerTest},
 * which shares the same application instance (and database) during the test run.
 */
@QuarkusTest
class AuthServiceTest {

    @Inject
    AuthService authService;

    @Inject
    UserRepository userRepository;

    // -----------------------------------------------------------------------
    //  Register
    // -----------------------------------------------------------------------

    @Test
    void register_newUser_persistsDocumentWithHashedPassword() {
        var request = new RegisterRequest("svcregister", "svc-register@test.local", "Password@Test1");

        var response = authService.register(request);

        assertNotNull(response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("svcregister", response.getUsername());
        assertTrue(response.getRoles().contains("USER"));

        var saved = userRepository.findByEmail("svc-register@test.local");
        assertTrue(saved.isPresent(), "User must be persisted in MongoDB");
        assertTrue(saved.get().isActive());
        assertNotNull(saved.get().getCreatedAt());
        // Password must be hashed — never stored as plain text
        assertFalse(saved.get().getPasswordHash().startsWith("Password"),
                "Password must be stored as BCrypt hash, not plain text");
    }

    @Test
    void register_duplicateEmail_throwsUserAlreadyExistsException() {
        var first = new RegisterRequest("svcdup1", "svc-dup@test.local", "Password@Test1");
        authService.register(first);

        var duplicate = new RegisterRequest("svcdup2", "svc-dup@test.local", "Password@Test1");
        assertThrows(UserAlreadyExistsException.class, () -> authService.register(duplicate));
    }

    @Test
    void register_duplicateUsername_throwsUserAlreadyExistsException() {
        var first = new RegisterRequest("svcsamename", "svc-un1@test.local", "Password@Test1");
        authService.register(first);

        var duplicate = new RegisterRequest("svcsamename", "svc-un2@test.local", "Password@Test1");
        assertThrows(UserAlreadyExistsException.class, () -> authService.register(duplicate));
    }

    // -----------------------------------------------------------------------
    //  Login
    // -----------------------------------------------------------------------

    @Test
    void login_validCredentials_returnsAuthResponseWithToken() {
        authService.register(new RegisterRequest("svclogin", "svc-login@test.local", "Password@Test1"));

        var login = new LoginRequest("svc-login@test.local", "Password@Test1");
        var response = authService.login(login);

        assertNotNull(response.getToken(), "JWT token must not be null");
        assertEquals("Bearer", response.getTokenType());
        assertEquals("svclogin", response.getUsername());
        assertTrue(response.getExpiresIn() > 0, "Token expiration must be positive");
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() {
        authService.register(new RegisterRequest("svcwrongpw", "svc-wrongpw@test.local", "Password@Test1"));

        var login = new LoginRequest("svc-wrongpw@test.local", "WrongPassword@999");
        assertThrows(InvalidCredentialsException.class, () -> authService.login(login));
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentialsException() {
        var login = new LoginRequest("nobody@test.local", "Password@Test1");
        assertThrows(InvalidCredentialsException.class, () -> authService.login(login));
    }
}
