package br.com.redestudio.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Request body for {@code POST /api/cloud-prices/bulk-upsert}. Capped per
 * request; {@code PriceLoadCli} splits a full load into
 * several requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudPriceBulkUpsertRequest", description = "Batch of cloud price entries to insert or update")
public class CloudPriceBulkUpsertRequest {

    public static final int MAX_ITEMS = 2000;

    @NotEmpty(message = "items must not be empty")
    @Size(max = MAX_ITEMS, message = "at most " + MAX_ITEMS + " items per request")
    private List<@Valid CloudPriceItemRequest> items;
}
