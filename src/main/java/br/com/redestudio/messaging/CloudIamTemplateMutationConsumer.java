package br.com.redestudio.messaging;

import br.com.redestudio.dtos.request.CloudIamTemplateItemRequest;
import br.com.redestudio.entities.CloudIamTemplateEntity;
import br.com.redestudio.repositories.CloudIamTemplateRepository;
import br.com.redestudio.services.CloudIamCatalogService;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.util.List;

/** Grava os lotes do catálogo IAM no Mongo e invalida o cache depois da escrita. */
@ApplicationScoped
public class CloudIamTemplateMutationConsumer {

    private static final Logger LOG = Logger.getLogger(CloudIamTemplateMutationConsumer.class);

    @Inject
    CloudIamTemplateRepository repository;

    @Inject
    CloudIamCatalogService catalogService;

    @Incoming("cloud-iam-bulk-upsert-in")
    public void onBulkUpsert(JsonObject payload) {
        CloudIamTemplateBulkUpsertMessage message = payload.mapTo(CloudIamTemplateBulkUpsertMessage.class);
        List<CloudIamTemplateEntity> entities = message.items().stream()
                .map(CloudIamTemplateEntity::fromRequest)
                .toList();
        int written = repository.bulkUpsert(entities);
        message.items().stream().map(CloudIamTemplateItemRequest::getProvider).distinct().forEach(catalogService::evict);
        LOG.infof("cloud-iam.bulk-upsert: %d item(s) received, %d inserted/modified", entities.size(), written);
    }
}
