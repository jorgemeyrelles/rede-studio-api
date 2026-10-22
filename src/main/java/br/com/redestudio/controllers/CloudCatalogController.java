package br.com.redestudio.controllers;

import br.com.redestudio.dtos.request.CloudIamTemplateBulkUpsertRequest;
import br.com.redestudio.dtos.response.CloudIamCatalogResponse;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.services.CloudIamCatalogService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
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

import java.util.Map;

/**
 * Catálogos de referência do Studio Cloud além dos preços — hoje o IAM
 * (modelos de grupo/role e políticas gerenciadas por família de provedor).
 * Leitura para qualquer usuário autenticado; carga só ADMIN.
 */
@Path("/api/cloud-catalog")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Cloud catalog", description = "Reference catalogs of the Cloud Studio (IAM templates per provider)")
@SecurityRequirement(name = "BearerAuth")
public class CloudCatalogController {

    @Inject
    CloudIamCatalogService iamCatalogService;

    @GET
    @Path("/iam")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(
            summary = "IAM catalog of a provider",
            description = "Group and role templates (with their default managed policies) and the managed policies offered to attach.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Catalog retrieved",
                content = @Content(schema = @Schema(implementation = CloudIamCatalogResponse.class))),
        @APIResponse(responseCode = "400", description = "Missing or invalid provider"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response iam(@QueryParam("provider") @NotNull CloudProvider provider) {
        return Response.ok(iamCatalogService.catalog(provider)).build();
    }

    @POST
    @Path("/iam/bulk-upsert")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Insert or update IAM catalog templates",
            description = "Upserts by (provider, key): new templates get created_at, every template gets updated_at. Persisted asynchronously.")
    @APIResponses({
        @APIResponse(responseCode = "202", description = "Items accepted"),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Not an ADMIN")
    })
    public Response iamBulkUpsert(@Valid CloudIamTemplateBulkUpsertRequest request) {
        int received = iamCatalogService.bulkUpsert(request);
        return Response.accepted(Map.of("received", received)).build();
    }
}
