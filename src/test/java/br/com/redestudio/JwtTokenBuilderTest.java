package br.com.redestudio;

import br.com.redestudio.components.JwtTokenBuilder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JwtTokenBuilder} — validates token structure
 * and claim content without mocking the signing infrastructure.
 *
 * <p>Uses the RSA test keypair from {@code src/test/resources/}
 * (privateKey.pem / publicKey.pem) which are resolved from classpath
 * via the base {@code application.yml} defaults.
 */
@QuarkusTest
class JwtTokenBuilderTest {

    @Inject
    JwtTokenBuilder jwtTokenBuilder;

    @Test
    void generateToken_returnedStringHasThreeJwtParts() {
        var token = jwtTokenBuilder.generateToken(
                "user@test.local", new ObjectId().toHexString(), "testuser", Set.of("USER"));

        var parts = token.split("\\.");
        assertEquals(3, parts.length,
                "JWT must have exactly 3 parts: header.payload.signature");
    }

    @Test
    void generateToken_payloadContainsSubjectClaim() {
        var token = jwtTokenBuilder.generateToken(
                "subject@test.local", new ObjectId().toHexString(), "subjectuser", Set.of("USER"));

        var payload = decodePayload(token);
        assertTrue(payload.contains("\"sub\""),        "Payload must contain 'sub' claim");
        assertTrue(payload.contains("subject@test.local"), "sub must match the provided subject");
    }

    @Test
    void generateToken_payloadContainsPreferredUsernameClaim() {
        var token = jwtTokenBuilder.generateToken(
                "pun@test.local", new ObjectId().toHexString(), "preferredname", Set.of("USER"));

        var payload = decodePayload(token);
        assertTrue(payload.contains("preferred_username"),
                "Payload must contain 'preferred_username' claim");
        assertTrue(payload.contains("preferredname"),
                "preferred_username must match the provided username");
    }

    @Test
    void generateToken_payloadContainsGroupsClaim() {
        var token = jwtTokenBuilder.generateToken(
                "roles@test.local", new ObjectId().toHexString(), "rolesuser", Set.of("USER", "ADMIN"));

        var payload = decodePayload(token);
        assertTrue(payload.contains("USER"),  "Payload must contain USER role");
        assertTrue(payload.contains("ADMIN"), "Payload must contain ADMIN role");
    }

    @Test
    void generateToken_payloadContainsIssuerClaim() {
        var token = jwtTokenBuilder.generateToken(
                "iss@test.local", new ObjectId().toHexString(), "issuser", Set.of("USER"));

        var payload = decodePayload(token);
        assertTrue(payload.contains("\"iss\""), "Payload must contain 'iss' claim");
    }

    @Test
    void generateToken_payloadContainsExpirationClaim() {
        var token = jwtTokenBuilder.generateToken(
                "exp@test.local", new ObjectId().toHexString(), "expuser", Set.of("USER"));

        var payload = decodePayload(token);
        assertTrue(payload.contains("\"exp\""), "Payload must contain 'exp' claim");
        assertTrue(payload.contains("\"iat\""), "Payload must contain 'iat' claim");
    }

    @Test
    void getExpirationSeconds_returnsPositiveValue() {
        assertTrue(jwtTokenBuilder.getExpirationSeconds() > 0,
                "Expiration seconds must be positive");
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    /**
     * Decodes the Base64url-encoded payload (second segment) of a JWT string.
     * No signature verification — for claim inspection only.
     */
    private static String decodePayload(String token) {
        var parts = token.split("\\.");
        assertEquals(3, parts.length, "Token must be a valid JWT (3 parts)");
        byte[] decoded = Base64.getUrlDecoder().decode(addPaddingIfNeeded(parts[1]));
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /** Base64url strings may omit padding — add '=' chars if needed. */
    private static String addPaddingIfNeeded(String base64url) {
        int padding = (4 - base64url.length() % 4) % 4;
        return base64url + "=".repeat(padding);
    }
}
