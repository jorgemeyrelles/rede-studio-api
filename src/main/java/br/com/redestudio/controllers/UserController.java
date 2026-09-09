package br.com.redestudio.controllers;

import br.com.redestudio.components.PasswordHasher;
import br.com.redestudio.dtos.request.ChangePasswordRequest;
import br.com.redestudio.dtos.request.SearchByEmailRequest;
import br.com.redestudio.dtos.request.SearchByNameRequest;
import br.com.redestudio.dtos.request.SearchByUsernameRequest;
import br.com.redestudio.dtos.request.UpdateUserRequest;
import br.com.redestudio.dtos.response.UserResponse;
import br.com.redestudio.services.UserService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
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

import java.util.List;

/**
 * REST controller exposing user management endpoints.
 *
 * <p>All routes require the {@code ADMIN} role.
 * The signed JWT must be sent as {@code Authorization: Bearer {token}}.
 *
 * <p>Sensitive fields (e.g. {@code passwordHash}) are never included
 * in responses — see {@link UserResponse}.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "User management — ADMIN only")
@SecurityRequirement(name = "BearerAuth")
@RolesAllowed("ADMIN")
public class UserController {

    @Inject
    UserService userService;

    @Inject
    PasswordHasher passwordHasher;

    /**
     * Returns all registered user accounts.
     *
     * @return HTTP 200 with a JSON array of {@link UserResponse}
     */
    @GET
    @Operation(
            summary = "List all users",
            description = "Returns every user document. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "User list retrieved",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public Response listAll() {
        List<UserResponse> users = userService.listAll().stream()
                .map(UserResponse::from)
                .toList();
        return Response.ok(users).build();
    }

    /**
     * Returns a single user identified by their email address.
     *
     * @param request payload carrying the email to look up
     * @return HTTP 200 with the matching {@link UserResponse}
     * @throws NotFoundException if no user with that email exists
     */
    @POST
    @Path("/search/email")
    @Operation(
            summary = "Find user by email",
            description = "Returns the user whose email matches the request body. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "User found",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges"),
        @APIResponse(responseCode = "404", description = "User not found")
    })
    public Response findByEmail(@Valid SearchByEmailRequest request) {
        UserResponse user = userService.findByEmail(request.getEmail())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + request.getEmail()));
        return Response.ok(user).build();
    }

    /**
     * Returns a single user identified by their username.
     *
     * @param request payload carrying the username to look up
     * @return HTTP 200 with the matching {@link UserResponse}
     * @throws NotFoundException if no user with that username exists
     */
    @POST
    @Path("/search/username")
    @Operation(
            summary = "Find user by username",
            description = "Returns the user whose username matches the request body. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "User found",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges"),
        @APIResponse(responseCode = "404", description = "User not found")
    })
    public Response findByUsername(@Valid SearchByUsernameRequest request) {
        UserResponse user = userService.findByUsername(request.getUsername())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found with username: " + request.getUsername()));
        return Response.ok(user).build();
    }

    /**
     * Returns users whose username contains the given substring, case-insensitively.
     *
     * @param request payload carrying the partial name to search for
     * @return HTTP 200 with a (possibly empty) JSON array of {@link UserResponse}
     */
    @POST
    @Path("/search/name")
    @Operation(
            summary = "Find users by partial name",
            description = "Returns users whose username contains the given substring (case-insensitive). Requires ADMIN role.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Search results (may be empty)",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public Response searchByName(@Valid SearchByNameRequest request) {
        List<UserResponse> users = userService.searchByNameContains(request.getName()).stream()
                .map(UserResponse::from)
                .toList();
        return Response.ok(users).build();
    }

    /**
     * Changes the password of the user identified by email.
     *
     * <p>The new password is hashed with BCrypt before being persisted.
     * The old password is NOT required — this is an ADMIN operation.
     *
     * @param email   the email of the target user
     * @param request payload containing the new plain-text password
     * @return HTTP 200 with the updated {@link UserResponse}
     */
    @PATCH
    @Path("/email/{email}/password")
    @Operation(
            summary = "Change user password",
            description = "Updates the BCrypt-hashed password for the specified user. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Password updated",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges"),
        @APIResponse(responseCode = "404", description = "User not found")
    })
    public Response changePassword(@PathParam("email") String email,
                                   @Valid ChangePasswordRequest request) {
        String newHash = passwordHasher.hash(request.getNewPassword());
        UserResponse updated = UserResponse.from(userService.changePassword(email, newHash));
        return Response.ok(updated).build();
    }

    /**
     * Partially updates a user's profile fields (username, email, roles, active).
     *
     * <p>Password is intentionally out of scope here — use
     * {@link #changePassword(String, ChangePasswordRequest)} for that.
     * Only fields present in the request body are changed.
     *
     * @param email   the current email of the user to update
     * @param request the fields to change (all optional)
     * @return HTTP 200 with the updated {@link UserResponse}
     */
    @PATCH
    @Path("/email/{email}")
    @Operation(
            summary = "Update user profile fields",
            description = "Partially updates username, email, roles and/or active status. Does not change password. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "User updated",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges"),
        @APIResponse(responseCode = "404", description = "User not found"),
        @APIResponse(responseCode = "409", description = "New username or email already in use")
    })
    public Response updateUser(@PathParam("email") String email,
                               @Valid UpdateUserRequest request) {
        UserResponse updated = UserResponse.from(userService.patchUser(email, request));
        return Response.ok(updated).build();
    }

    /**
     * Deletes the user identified by email.
     *
     * @param email the email of the user to delete
     * @return HTTP 204 No Content on success
     */
    @DELETE
    @Path("/email/{email}")
    @Operation(
            summary = "Delete user by email",
            description = "Permanently removes the user document from MongoDB. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "User deleted successfully"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges"),
        @APIResponse(responseCode = "404", description = "User not found")
    })
    public Response deleteByEmail(@PathParam("email") String email) {
        userService.deleteByEmail(email);
        return Response.noContent().build();
    }
}
