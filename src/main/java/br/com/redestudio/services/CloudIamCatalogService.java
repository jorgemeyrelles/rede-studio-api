package br.com.redestudio.services;

import br.com.redestudio.dtos.request.CloudIamTemplateBulkUpsertRequest;
import br.com.redestudio.dtos.response.CloudIamCatalogResponse;
import br.com.redestudio.dtos.response.CloudIamTemplateResponse;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.entities.IamTemplateKind;
import br.com.redestudio.messaging.CloudIamTemplateBulkUpsertMessage;
import br.com.redestudio.messaging.CloudPriceMutationProducer;
import br.com.redestudio.repositories.CloudIamTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo IAM do Studio Cloud (modelos de grupo/role e políticas
 * gerenciadas), por família de provedor. Leitura Redis-primeiro (24h);
 * escrita ADMIN pela fila {@code cloud-iam.bulk-upsert}, com o cache
 * invalidado na requisição e de novo no consumer, depois do Mongo — mesma
 * regra de {@link CloudPriceService}.
 */
@ApplicationScoped
public class CloudIamCatalogService {

    private static final Logger LOG = Logger.getLogger(CloudIamCatalogService.class);
    private static final Duration TTL = Duration.ofHours(24);

    @Inject
    CloudIamTemplateRepository repository;

    @Inject
    CloudPriceMutationProducer producer;

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    static String cacheKey(CloudProvider provider) {
        return "cloud-iam:" + provider.name();
    }

    public CloudIamCatalogResponse catalog(CloudProvider provider) {
        return readCache(provider).orElseGet(() -> {
            List<CloudIamTemplateResponse> all = repository.findByProvider(provider).stream()
                    .map(CloudIamTemplateResponse::from)
                    .toList();
            CloudIamCatalogResponse catalog = new CloudIamCatalogResponse(
                    provider, ofKind(all, IamTemplateKind.GROUP), ofKind(all, IamTemplateKind.ROLE),
                    ofKind(all, IamTemplateKind.POLICY));
            if (!all.isEmpty()) {
                writeCache(provider, catalog);
            }
            return catalog;
        });
    }

    /** @return quantos itens foram aceitos (gravação assíncrona). */
    public int bulkUpsert(CloudIamTemplateBulkUpsertRequest request) {
        request.getItems().stream().map(item -> item.getProvider()).distinct().forEach(this::evict);
        producer.publishIamBulkUpsert(new CloudIamTemplateBulkUpsertMessage(List.copyOf(request.getItems())));
        return request.getItems().size();
    }

    public void evict(CloudProvider provider) {
        try {
            redisDataSource.key().del(cacheKey(provider));
        } catch (Exception e) {
            LOG.warnf(e, "Redis evict failed for IAM catalog %s", provider);
        }
    }

    private static List<CloudIamTemplateResponse> ofKind(List<CloudIamTemplateResponse> all, IamTemplateKind kind) {
        return all.stream().filter(t -> t.getKind() == kind).toList();
    }

    private Optional<CloudIamCatalogResponse> readCache(CloudProvider provider) {
        try {
            String raw = redisDataSource.value(String.class).get(cacheKey(provider));
            return raw == null ? Optional.empty() : Optional.of(objectMapper.readValue(raw, CloudIamCatalogResponse.class));
        } catch (Exception e) {
            LOG.warnf(e, "Redis read failed for IAM catalog %s", provider);
            return Optional.empty();
        }
    }

    private void writeCache(CloudProvider provider, CloudIamCatalogResponse catalog) {
        try {
            redisDataSource.value(String.class).setex(cacheKey(provider), TTL.toSeconds(),
                    objectMapper.writeValueAsString(catalog));
        } catch (Exception e) {
            LOG.warnf(e, "Redis write failed for IAM catalog %s", provider);
        }
    }
}
