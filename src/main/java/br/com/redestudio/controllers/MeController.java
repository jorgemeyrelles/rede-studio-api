package br.com.redestudio.controllers;

import br.com.redestudio.dtos.request.UpdateMyProfileRequest;
import br.com.redestudio.dtos.response.UserResponse;
import br.com.redestudio.services.UserService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST controller exposing self-service profile endpoints.
 *
 * <p>Unlike {@link UserController} (ADMIN-only, operates on an arbitrary
 * email path param), every route here always resolves the target user from
 * the caller's own JWT ({@link JsonWebToken#getSubject()}) — there is no
 * way to read or edit anyone else's profile through this controller.
 */
@Path("/api/users/me")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Me", description = "Self-service profile management")
@SecurityRequirement(name = "BearerAuth")
@RolesAllowed({"USER", "ADMIN"})
public class MeController {

    @Inject
    UserService userService;

    @Inject
    JsonWebToken jwt;

    /**
     * Returns the authenticated user's own profile.
     *
     * @return HTTP 200 with the caller's {@link UserResponse}
     */
    @GET
    @Operation(
            summary = "Get my profile",
            description = "Returns the profile of the authenticated user.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Profile retrieved",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response getMyProfile() {
        UserResponse response = userService.findByEmail(jwt.getSubject())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));
        return Response.ok(response).build();
    }

    /**
     * Partially updates the authenticated user's own profile
     * (username and/or preferred language only).
     *
     * @param request the fields to change (all optional)
     * @return HTTP 200 with the updated {@link UserResponse}
     */
    @PATCH
    @Operation(
            summary = "Update my profile",
            description = "Updates the authenticated user's own username and/or preferred language. "
                    + "Email, roles and active status cannot be changed here.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Profile updated",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "409", description = "New username already in use")
    })
    public Response updateMyProfile(@Valid UpdateMyProfileRequest request) {
        UserResponse response = UserResponse.from(userService.patchOwnProfile(jwt.getSubject(), request));
        return Response.ok(response).build();
    }
}
