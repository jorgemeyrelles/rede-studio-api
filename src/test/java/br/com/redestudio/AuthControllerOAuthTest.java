package br.com.redestudio;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Integration tests for {@code POST /api/auth/oauth/{provider}} — full HTTP
 * stack against {@link FakeOAuthTokenVerifier} (see its javadoc for the fake
 * token format), no real Google/Microsoft network calls.
 */
@QuarkusTest
class AuthControllerOAuthTest {

    @Test
    void loginWithOAuth_validToken_returns200WithToken() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                    { "idToken": "valid:sub-ctrl-1:ctrl-oauth@test.local:Ctrl User" }
                    """)
        .when()
            .post("/api/auth/oauth/google")
        .then()
            .statusCode(200)
            .body("token",     notNullValue())
            .body("tokenType", equalTo("Bearer"));
    }

    @Test
    void loginWithOAuth_invalidToken_returns401Unauthorized() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                    { "idToken": "garbage" }
                    """)
        .when()
            .post("/api/auth/oauth/google")
        .then()
            .statusCode(401)
            .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    void loginWithOAuth_unsupportedProvider_returns401Unauthorized() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                    { "idToken": "valid:sub-x:x@test.local:X" }
                    """)
        .when()
            .post("/api/auth/oauth/facebook")
        .then()
            .statusCode(401)
            .body("error", equalTo("UNAUTHORIZED"));
    }

    @Test
    void loginWithOAuth_blankIdToken_returns400ValidationError() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                    { "idToken": "" }
                    """)
        .when()
            .post("/api/auth/oauth/google")
        .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"));
    }
}
