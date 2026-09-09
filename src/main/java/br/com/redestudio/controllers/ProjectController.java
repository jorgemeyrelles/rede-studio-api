package br.com.redestudio.controllers;

import br.com.redestudio.dtos.request.CreateProjectRequest;
import br.com.redestudio.dtos.request.RenameProjectRequest;
import br.com.redestudio.dtos.request.SaveSnapshotRequest;
import br.com.redestudio.dtos.response.ProjectResponse;
import br.com.redestudio.dtos.response.ProjectSummaryResponse;
import br.com.redestudio.services.ProjectService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST controller exposing project management endpoints.
 *
 * <p>All routes under {@code /api/projects} require an authenticated user
 * (any role). Ownership is always resolved from the JWT {@code uid} claim
 * (the user's Mongo {@code _id} — see {@code JwtTokenBuilder}), never from
 * a client-supplied field, so a user can only ever see, edit or delete
 * their own projects.
 */
@Path("/api/projects")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Projects", description = "Network topology project management")
@SecurityRequirement(name = "BearerAuth")
@RolesAllowed({"USER", "ADMIN"})
public class ProjectController {

    @Inject
    ProjectService projectService;

    @Inject
    JsonWebToken jwt;

    private ObjectId currentUserId() {
        return new ObjectId(jwt.getClaim("uid").toString());
    }

    /**
     * Creates a new project owned by the authenticated user.
     *
     * @param request name and initial network state snapshot
     * @return HTTP 201 with the created {@link ProjectResponse}
     */
    @POST
    @Operation(
            summary = "Create a new project",
            description = "Creates a project owned by the authenticated user, persisting the given initial network state as-is.")
    @APIResponses({
        @APIResponse(
                responseCode = "201",
                description = "Project created successfully",
                content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response create(@Valid CreateProjectRequest request) {
        ProjectResponse response = projectService.create(currentUserId(), request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /**
     * Lists every project owned by the authenticated user.
     *
     * @return HTTP 200 with a JSON array of {@link ProjectSummaryResponse}
     */
    @GET
    @Operation(
            summary = "List my projects",
            description = "Returns every project owned by the authenticated user.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Project list retrieved",
                content = @Content(schema = @Schema(implementation = ProjectSummaryResponse.class))),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response listMine() {
        List<ProjectSummaryResponse> projects = projectService.listMine(currentUserId());
        return Response.ok(projects).build();
    }

    /**
     * Returns a single project owned by the authenticated user.
     *
     * @param id the project id
     * @return HTTP 200 with the matching {@link ProjectResponse}
     */
    @GET
    @Path("/{id}")
    @Operation(
            summary = "Get a project",
            description = "Returns the full project (including network state) if owned by the authenticated user.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Project found",
                content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "404", description = "Project not found or not owned by this user")
    })
    public Response getOne(@PathParam("id") String id) {
        ProjectResponse response = projectService.getOwned(id, currentUserId());
        return Response.ok(response).build();
    }

    /**
     * Renames a project owned by the authenticated user.
     *
     * @param id      the project id
     * @param request the new name
     * @return HTTP 200 with the updated {@link ProjectSummaryResponse}
     */
    @PATCH
    @Path("/{id}")
    @Operation(
            summary = "Rename a project",
            description = "Updates the display name of a project owned by the authenticated user.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Project renamed",
                content = @Content(schema = @Schema(implementation = ProjectSummaryResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "404", description = "Project not found or not owned by this user")
    })
    public Response rename(@PathParam("id") String id, @Valid RenameProjectRequest request) {
        ProjectSummaryResponse response = projectService.rename(id, currentUserId(), request.getName());
        return Response.ok(response).build();
    }

    /**
     * Saves a full network state snapshot for a project owned by the
     * authenticated user — used by the frontend's autosave.
     *
     * @param id      the project id
     * @param request the new network state snapshot
     * @return HTTP 200 with the updated {@link ProjectSummaryResponse}
     */
    @PUT
    @Path("/{id}/snapshot")
    @Operation(
            summary = "Save a project snapshot",
            description = "Overwrites the network state of a project owned by the authenticated user (autosave).")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Snapshot saved",
                content = @Content(schema = @Schema(implementation = ProjectSummaryResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "404", description = "Project not found or not owned by this user")
    })
    public Response saveSnapshot(@PathParam("id") String id, @Valid SaveSnapshotRequest request) {
        ProjectSummaryResponse response =
                projectService.saveSnapshot(id, currentUserId(), request.getNetworkState());
        return Response.ok(response).build();
    }

    /**
     * Deletes a project owned by the authenticated user.
     *
     * @param id the project id
     * @return HTTP 204 No Content on success
     */
    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Delete a project",
            description = "Permanently removes a project owned by the authenticated user.")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Project deleted successfully"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "404", description = "Project not found or not owned by this user")
    })
    public Response delete(@PathParam("id") String id) {
        projectService.delete(id, currentUserId());
        return Response.noContent().build();
    }
}
