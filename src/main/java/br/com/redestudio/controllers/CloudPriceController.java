package br.com.redestudio.controllers;

import br.com.redestudio.dtos.request.CloudPriceBulkUpsertRequest;
import br.com.redestudio.dtos.response.CloudPriceBulkUpsertResponse;
import br.com.redestudio.dtos.response.CloudPriceCatalogResponse;
import br.com.redestudio.dtos.response.CloudPriceMetaResponse;
import br.com.redestudio.dtos.response.CloudPriceResponse;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.services.CloudPriceService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

import java.util.List;

/**
 * REST controller for the cloud price database ({@code rede_cloud}).
 *
 * <p>Roles split per method, same as {@code EquipmentController}: reads are
 * for any authenticated user (the cloud Studio's cost engine and region
 * selector), the bulk upsert is ADMIN-only (used by
 * {@code PriceLoadCli}).
 */
@Path("/api/cloud-prices")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Cloud prices", description = "Cloud price catalog (USD and BRL) per provider and region")
@SecurityRequirement(name = "BearerAuth")
public class CloudPriceController {

    private static final String REGION_PATTERN = "[a-z0-9-]{2,32}";

    @Inject
    CloudPriceService cloudPriceService;

    @GET
    @Path("/meta")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(
            summary = "Regions with prices loaded",
            description = "Lists, per provider family, every region with prices, its item count, last update and the FX quote used.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Summary retrieved",
                content = @Content(schema = @Schema(implementation = CloudPriceMetaResponse.class))),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response meta() {
        return Response.ok(cloudPriceService.meta()).build();
    }

    @GET
    @Path("/catalog")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(
            summary = "Price catalog of one region",
            description = "Every price entry of a provider in a region, compact, with USD and BRL. Unknown regions return an empty list.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Catalog retrieved",
                content = @Content(schema = @Schema(implementation = CloudPriceCatalogResponse.class))),
        @APIResponse(responseCode = "400", description = "Missing or invalid provider/region"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response catalog(
            @QueryParam("provider") @NotNull CloudProvider provider,
            @QueryParam("region") @NotBlank @Pattern(regexp = REGION_PATTERN) String region) {
        return Response.ok(cloudPriceService.catalog(provider, region)).build();
    }

    @GET
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(
            summary = "Price entries of one resource type",
            description = "Full entries (attributes, source, timestamps) of a resource type in a region — e.g. every EC2 instance type.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Entries retrieved",
                content = @Content(schema = @Schema(implementation = CloudPriceResponse.class))),
        @APIResponse(responseCode = "400", description = "Missing or invalid parameters"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public Response search(
            @QueryParam("provider") @NotNull CloudProvider provider,
            @QueryParam("region") @NotBlank @Pattern(regexp = REGION_PATTERN) String region,
            @QueryParam("resourceType") @NotBlank String resourceType) {
        List<CloudPriceResponse> items = cloudPriceService.search(provider, region, resourceType);
        return Response.ok(items).build();
    }

    @POST
    @Path("/bulk-upsert")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Insert or update cloud prices",
            description = "Upserts by (provider, rateCode): new entries get created_at, every entry gets updated_at. "
                    + "Persisted asynchronously; the response only acknowledges what was queued.")
    @APIResponses({
        @APIResponse(
                responseCode = "202",
                description = "Items accepted",
                content = @Content(schema = @Schema(implementation = CloudPriceBulkUpsertResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @APIResponse(responseCode = "403", description = "Not an ADMIN")
    })
    public Response bulkUpsert(@Valid CloudPriceBulkUpsertRequest request) {
        return Response.accepted(cloudPriceService.bulkUpsert(request)).build();
    }
}
