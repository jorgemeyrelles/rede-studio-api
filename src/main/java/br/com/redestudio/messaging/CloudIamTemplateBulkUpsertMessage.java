package br.com.redestudio.messaging;

import br.com.redestudio.dtos.request.CloudIamTemplateItemRequest;

import java.util.List;

/** Payload publicado na fila {@code cloud-iam.bulk-upsert}. */
public record CloudIamTemplateBulkUpsertMessage(List<CloudIamTemplateItemRequest> items) {
}
