package br.com.redestudio.controllers;

import br.com.redestudio.dtos.request.LoginRequest;
import br.com.redestudio.dtos.request.RegisterRequest;
import br.com.redestudio.dtos.response.AuthResponse;
import br.com.redestudio.services.AuthService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST controller exposing authentication endpoints.
 *
 * <p>All routes under {@code /api/auth} are public ({@code @PermitAll}).
 * No JWT is required to reach register or login.
 *
 * <p>Successful responses return HTTP 201 (register) or HTTP 200 (login)
 * with an {@link AuthResponse} body containing the signed JWT.
 */
@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "User registration and login")
public class AuthController {

    @Inject
    AuthService authService;

    /**
     * Registers a new user account.
     *
     * <p>On success the account is created immediately and a JWT is returned
     * so the client does not need a separate login call after registration.
     *
     * @param request registration payload
     * @return HTTP 201 with {@link AuthResponse}
     */
    @POST
    @Path("/register")
    @PermitAll
    @Operation(
            summary = "Register a new user",
            description = "Creates a new account and returns a signed JWT on success.")
    @APIResponses({
        @APIResponse(
                responseCode = "201",
                description = "User registered successfully",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "409", description = "Email or username already taken")
    })
    public Response register(@Valid RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /**
     * Authenticates an existing user with email and password.
     *
     * @param request login credentials
     * @return HTTP 200 with {@link AuthResponse}
     */
    @POST
    @Path("/login")
    @PermitAll
    @Operation(
            summary = "Login with email and password",
            description = "Validates credentials and returns a signed JWT on success.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Login successful",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Invalid email or password")
    })
    public Response login(@Valid LoginRequest request) {
        AuthResponse response = authService.login(request);
        return Response.ok(response).build();
    }
}
