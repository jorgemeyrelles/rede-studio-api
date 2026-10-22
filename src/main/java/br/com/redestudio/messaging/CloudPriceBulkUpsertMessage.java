package br.com.redestudio.messaging;

import br.com.redestudio.dtos.request.CloudPriceItemRequest;

import java.util.List;

/**
 * Payload published to the {@code cloud-price.bulk-upsert} RabbitMQ queue
 * — one batch of at most {@code CloudPriceService.BATCH_SIZE} items.
 */
public record CloudPriceBulkUpsertMessage(List<CloudPriceItemRequest> items) {
}
