package br.com.redestudio.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Acknowledgement of a {@code bulk-upsert}: items accepted and queued (persisted asynchronously). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudPriceBulkUpsertResponse", description = "Items accepted for asynchronous upsert")
public class CloudPriceBulkUpsertResponse {

    private int received;
    private int batches;
}
