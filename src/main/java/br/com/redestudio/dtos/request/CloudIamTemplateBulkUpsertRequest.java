package br.com.redestudio.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/** Corpo de {@code POST /api/cloud-catalog/iam/bulk-upsert}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudIamTemplateBulkUpsertRequest", description = "Batch of IAM catalog templates to insert or update")
public class CloudIamTemplateBulkUpsertRequest {

    @NotEmpty(message = "items must not be empty")
    @Size(max = 2000, message = "at most 2000 items per request")
    private List<@Valid CloudIamTemplateItemRequest> items;
}
