package br.com.redestudio.runners;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.components.pricing.AwsPriceListClient;
import br.com.redestudio.components.pricing.AwsPriceLoader;
import br.com.redestudio.components.pricing.PtaxClient;
import br.com.redestudio.components.pricing.PtaxClient.FxQuote;
import br.com.redestudio.dtos.request.CloudPriceItemRequest;
import br.com.redestudio.entities.CloudPriceEntity;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.repositories.CloudPriceRepository;
import br.com.redestudio.services.CloudPriceCacheService;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Migration de preços cloud, <b>só no ambiente local</b>
 * ({@code app.cloud-pricing.migration.enabled}, ligada apenas no perfil
 * {@code %dev}): se o banco de preços ({@code app.cloud-pricing.database})
 * não existe ou a coleção {@code cloud_prices} está vazia, carrega da AWS
 * Price List as Regiões configuradas (+ os serviços globais) com a PTAX do
 * dia. Com dados presentes, não faz nada — como o {@link DevSeedRunner}.
 *
 * <p>Roda numa thread própria depois do boot (a leitura dos CSVs leva
 * minutos e o servidor HTTP não espera por ela). Os itens ficam em memória
 * até o fim e são gravados de uma vez: se a carga falhar no meio, nada é
 * gravado e o próximo boot tenta de novo.
 *
 * <p>Produção nunca roda isto: a carga de lá é o comando
 * {@code PriceLoadCli}, com aval (skill {@code cloud}).
 */
@ApplicationScoped
public class CloudPriceMigration {

    private static final Logger LOG = Logger.getLogger(CloudPriceMigration.class);

    @ConfigProperty(name = "app.cloud-pricing.migration.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "app.cloud-pricing.migration.regions", defaultValue = "sa-east-1,us-east-1")
    List<String> regions;

    @ConfigProperty(name = "app.cloud-pricing.migration.include-global", defaultValue = "true")
    boolean includeGlobal;

    @Inject
    CloudPriceRepository repository;

    @Inject
    CloudPriceCacheService cacheService;

    @Inject
    AwsPriceListClient priceList;

    @Inject
    PtaxClient ptax;

    /** Depois do {@link StartupRunner} (índices) e do {@link DevSeedRunner}. */
    void onStart(@Observes @Priority(3100) StartupEvent event) {
        if (!enabled) {
            return;
        }
        if (!repository.isEmpty()) {
            LOG.infof("[Migration] %s já tem dados — carga de preços ignorada", CollectionNames.CLOUD_PRICES);
            return;
        }
        Thread.ofVirtual().name("cloud-price-migration").start(() -> {
            try {
                migrateIfEmpty();
            } catch (RuntimeException e) {
                LOG.errorf(e, "[Migration] carga de preços falhou — nada gravado; o próximo boot tenta de novo");
            }
        });
    }

    /**
     * Carrega e grava os preços se a coleção estiver vazia.
     *
     * @return itens gravados (0 quando já havia dados)
     */
    public int migrateIfEmpty() {
        if (!repository.isEmpty()) {
            return 0;
        }
        FxQuote fx = ptax.quote(LocalDate.now(ZoneOffset.UTC));
        LOG.infof("[Migration] preços AWS de %s (+ global: %s), US$ 1 = R$ %s", regions, includeGlobal, fx.rate());

        List<CloudPriceEntity> items = new ArrayList<>();
        new AwsPriceLoader(priceList).load(
                new AwsPriceLoader.Options(regions, false, includeGlobal, Set.of()),
                fx,
                batch -> batch.stream().map(CloudPriceItemRequest::toEntity).forEach(items::add),
                line -> LOG.info("[Migration] " + line));

        int written = 0;
        for (int start = 0; start < items.size(); start += 1000) {
            written += repository.bulkUpsert(items.subList(start, Math.min(start + 1000, items.size())));
        }
        items.stream()
                .map(CloudPriceEntity::getRegion)
                .distinct()
                .forEach(region -> cacheService.evict(CloudProvider.AWS, region));
        LOG.infof("[Migration] %d preço(s) gravado(s) em %s", written, repository.databaseName());
        return written;
    }
}
