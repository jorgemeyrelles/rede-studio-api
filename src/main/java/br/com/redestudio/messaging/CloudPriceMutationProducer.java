package br.com.redestudio.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Publishes cloud catalog batches (prices and IAM templates) to their
 * dedicated queues — one queue per operation, consumed by
 * {@link CloudPriceMutationConsumer} / {@link CloudIamTemplateMutationConsumer}.
 */
@ApplicationScoped
public class CloudPriceMutationProducer {

    @Channel("cloud-price-bulk-upsert-out")
    Emitter<CloudPriceBulkUpsertMessage> bulkUpsertEmitter;

    @Channel("cloud-iam-bulk-upsert-out")
    Emitter<CloudIamTemplateBulkUpsertMessage> iamBulkUpsertEmitter;

    public void publishBulkUpsert(CloudPriceBulkUpsertMessage message) {
        bulkUpsertEmitter.send(message);
    }

    public void publishIamBulkUpsert(CloudIamTemplateBulkUpsertMessage message) {
        iamBulkUpsertEmitter.send(message);
    }
}
