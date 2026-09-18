package br.com.redestudio.controllers;

import br.com.redestudio.dtos.request.BulkCreateEquipmentsRequest;
import br.com.redestudio.dtos.request.CreateEquipmentRequest;
import br.com.redestudio.dtos.request.UpdateEquipmentRequest;
import br.com.redestudio.dtos.response.EquipmentResponse;
import br.com.redestudio.services.EquipmentService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
 * REST controller exposing the equipment catalog endpoints.
 *
 * <p>Unlike every other controller in this project so far, roles are split
 * <em>per method</em> instead of for the whole class: reads
 * ({@code GET}) are available to any authenticated user (same as
 * {@code ProjectController}), while writes ({@code POST}/{@code PATCH}/
 * {@code DELETE}) require {@code ADMIN} (same as {@code UserController}) —
 * the catalog is a shared, admin-curated reference list, not
 * per-user data. {@code JwtAuthenticationFilter} only checks that a
 * well-formed bearer token is present on every route here (equipments
 * routes aren't in its public whitelist); the actual role check per method
 * is enforced by {@code @RolesAllowed} below, evaluated by SmallRye JWT —
 * no filter changes were needed for this split.
 */
@Path("/api/equipments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Equipments", description = "Network equipment catalog")
@SecurityRequirement(name = "BearerAuth")
public class EquipmentController {

    @Inject
    EquipmentService equipmentService;

    /**
     * Lists every equipment in the catalog.
     *
     * @return HTTP 200 with a JSON array of {@link EquipmentResponse}
     */
    @GET
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(
            summary = "List all equipment",
            description = "Returns every equipment catalog entry.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Equipment list retrieved",
                content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response listAll() {
        List<EquipmentResponse> equipment = equipmentService.listAll();
        return Response.ok(equipment).build();
    }

    /**
     * Lists every equipment of the given brand.
     *
     * @param brand the brand to filter by
     * @return HTTP 200 with a JSON array of {@link EquipmentResponse}
     */
    @GET
    @Path("/brand/{brand}")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(
            summary = "List equipment by brand",
            description = "Returns every equipment catalog entry matching the given brand.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Search results (may be empty)",
                content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response listByBrand(@PathParam("brand") String brand) {
        List<EquipmentResponse> equipment = equipmentService.listByBrand(brand);
        return Response.ok(equipment).build();
    }

    /**
     * Searches equipment by partial model name.
     *
     * @param name the substring to search for (case-insensitive)
     * @return HTTP 200 with a JSON array of {@link EquipmentResponse}
     */
    @GET
    @Path("/search")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(
            summary = "Search equipment by partial model name",
            description = "Returns equipment whose model contains the given substring (case-insensitive).")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Search results (may be empty)",
                content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response searchByName(@QueryParam("name") String name) {
        List<EquipmentResponse> equipment = equipmentService.searchByModelContains(name == null ? "" : name);
        return Response.ok(equipment).build();
    }

    /**
     * Returns a single equipment by id.
     *
     * @param id the equipment id
     * @return HTTP 200 with the matching {@link EquipmentResponse}
     */
    @GET
    @Path("/{id}")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(
            summary = "Get equipment by id",
            description = "Returns a single equipment catalog entry.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Equipment found",
                content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "404", description = "Equipment not found")
    })
    public Response getOne(@PathParam("id") String id) {
        EquipmentResponse response = equipmentService.getById(id);
        return Response.ok(response).build();
    }

    /**
     * Creates a new equipment catalog entry.
     *
     * @param request the equipment data
     * @return HTTP 201 with the created {@link EquipmentResponse}
     */
    @POST
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Create equipment",
            description = "Creates a single equipment catalog entry. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(
                responseCode = "201",
                description = "Equipment created successfully",
                content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public Response create(@Valid CreateEquipmentRequest request) {
        EquipmentResponse response = equipmentService.create(request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /**
     * Creates many equipment catalog entries in one call.
     *
     * @param request the equipment entries to create
     * @return HTTP 201 with the created {@link EquipmentResponse} list
     */
    @POST
    @Path("/bulk")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Bulk-create equipment",
            description = "Creates many equipment catalog entries in one call. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(
                responseCode = "201",
                description = "Equipment created successfully",
                content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public Response bulkCreate(@Valid BulkCreateEquipmentsRequest request) {
        List<EquipmentResponse> response = equipmentService.bulkCreate(request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /**
     * Partially updates an equipment catalog entry.
     *
     * @param id      the equipment id
     * @param request the fields to change (all optional)
     * @return HTTP 200 with the updated {@link EquipmentResponse}
     */
    @PATCH
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Update equipment",
            description = "Partially updates an equipment catalog entry. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Equipment updated",
                content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges"),
        @APIResponse(responseCode = "404", description = "Equipment not found")
    })
    public Response update(@PathParam("id") String id, @Valid UpdateEquipmentRequest request) {
        EquipmentResponse response = equipmentService.update(id, request);
        return Response.ok(response).build();
    }

    /**
     * Deletes an equipment catalog entry.
     *
     * @param id the equipment id
     * @return HTTP 204 No Content on success
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Delete equipment",
            description = "Permanently removes an equipment catalog entry. Requires ADMIN role.")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Equipment deleted successfully"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Insufficient privileges"),
        @APIResponse(responseCode = "404", description = "Equipment not found")
    })
    public Response delete(@PathParam("id") String id) {
        equipmentService.delete(id);
        return Response.noContent().build();
    }
}
