package br.com.redestudio.runners;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.components.EquipmentFunctions;
import br.com.redestudio.dtos.request.BulkCreateEquipmentsRequest;
import br.com.redestudio.dtos.request.CloudIamTemplateBulkUpsertRequest;
import br.com.redestudio.dtos.request.CreateEquipmentRequest;
import br.com.redestudio.entities.CloudIamTemplateEntity;
import br.com.redestudio.entities.EquipmentEntity;
import br.com.redestudio.entities.EquipmentPrice;
import br.com.redestudio.repositories.CloudIamTemplateRepository;
import br.com.redestudio.repositories.EquipmentRepository;
import br.com.redestudio.services.CloudIamCatalogService;
import br.com.redestudio.services.EquipmentCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.bson.BsonType;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Migrations e seeds de dados que rodam <b>só no ambiente local</b>
 * ({@code app.dev-seed.enabled}, ligado apenas no perfil {@code %dev} — o
 * Docker Compose de {@code docker/dev}): o container de dev é recriado com
 * frequência e nasceria sem dados. Em produção a flag fica desligada e toda
 * carga é manual (comando {@code PriceLoadCli upload}, ver a skill {@code cloud}).
 *
 * <p>Cada seed só roda se a coleção de destino <b>não existir ou estiver
 * vazia</b> — o {@link StartupRunner} cria os índices antes, e criar índice
 * já cria a coleção, então "vazia" é o critério que cobre os dois casos.
 * Dados já presentes (inclusive editados à mão) nunca são sobrescritos.
 *
 * <p>Fontes, as mesmas usadas no envio manual para produção:
 * <ul>
 *   <li>{@code seed/equipments/equipments.json} — formato do
 *       {@code POST /api/equipments/bulk};</li>
 *   <li>{@code seed/cloud-catalog/*.json} — formato do
 *       {@code POST /api/cloud-catalog/iam/bulk-upsert}.</li>
 * </ul>
 *
 * <p>Escreve direto no Mongo (sem a fila), como as demais tarefas de boot:
 * roda antes do servidor HTTP aceitar conexões, não há requisição nem
 * leitura concorrente a proteger.
 */
@ApplicationScoped
public class DevSeedRunner {

    private static final Logger LOG = Logger.getLogger(DevSeedRunner.class);

    static final String EQUIPMENT_SEED = "seed/equipments/equipments.json";

    /** Arquivos de seed IAM por família de provedor (Azure/GCP/OCI entram aqui). */
    static final List<String> IAM_SEEDS = List.of("seed/cloud-catalog/aws-iam.json");

    @ConfigProperty(name = "app.dev-seed.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "rede_studio_dev")
    String databaseName;

    @Inject
    MongoClient mongoClient;

    @Inject
    EquipmentRepository equipmentRepository;

    @Inject
    EquipmentCacheService equipmentCacheService;

    @Inject
    CloudIamTemplateRepository iamRepository;

    @Inject
    CloudIamCatalogService catalogService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Depois do {@link StartupRunner} (que cria coleções e índices):
     * observers sem {@code @Priority} rodam em 2500 e número maior roda depois.
     */
    void onStart(@Observes @Priority(3000) StartupEvent event) {
        if (!enabled) {
            return;
        }
        migrateEquipmentFunctionToList();
        if (equipmentRepository.count() == 0) {
            seedEquipments();
        } else {
            LOG.infof("[Seed] %s já tem dados — seed de equipamentos ignorado", CollectionNames.EQUIPMENTS);
        }
        if (iamRepository.isEmpty()) {
            seedIam();
        } else {
            LOG.infof("[Seed] %s já tem dados — seed IAM ignorado", CollectionNames.CLOUD_IAM_TEMPLATES);
        }
    }

    /**
     * Converte {@code equipments.function} da string antiga
     * ("roteador/firewall/gateway") para a lista de termos (ver
     * {@link EquipmentFunctions#fromLegacy}). Só existe para bases locais
     * carregadas a partir de exports antigos — produção já foi migrada em
     * 2026-09-23. Só toca documentos cujo campo ainda é string, então é
     * idempotente e não faz nada numa base já migrada ou recém-semeada.
     *
     * <p>Armadilha do MongoDB: {@code {function: {$type: "string"}}} também
     * casa ARRAYS que contêm alguma string — os documentos já migrados. Só
     * filtrar por STRING fazia o segundo boot cair em
     * {@code ClassCastException} e a API ficava em loop de restart
     * (incidente 2026-10-16). Por isso o filtro exclui arrays e o loop ainda
     * confere o tipo real do campo.
     */
    void migrateEquipmentFunctionToList() {
        var equipments = mongoClient
                .getDatabase(databaseName)
                .getCollection(CollectionNames.EQUIPMENTS);

        var legacyFilter = Filters.and(
                Filters.type("function", BsonType.STRING),
                Filters.not(Filters.type("function", BsonType.ARRAY)));

        int migrated = 0;
        for (Document doc : equipments.find(legacyFilter)) {
            if (!(doc.get("function") instanceof String legacy)) {
                continue;
            }
            equipments.updateOne(
                    Filters.eq("_id", doc.get("_id")),
                    Updates.set("function", EquipmentFunctions.fromLegacy(legacy)));
            migrated++;
        }
        if (migrated > 0) {
            equipmentCacheService.evictAll();
            LOG.infof("[Migration] equipments.function string → list: %d documento(s) migrado(s)", migrated);
        }
    }

    /** Lê o seed de equipamentos empacotado (público para o teste validar o arquivo). */
    public List<CreateEquipmentRequest> loadEquipmentSeed() throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(EQUIPMENT_SEED)) {
            if (input == null) {
                throw new IOException(EQUIPMENT_SEED + " não encontrado no classpath");
            }
            return objectMapper.readValue(input, BulkCreateEquipmentsRequest.class).getItems();
        }
    }

    /** Insere o catálogo de equipamentos; um arquivo inválido é registrado e não derruba o boot. */
    void seedEquipments() {
        try {
            List<EquipmentEntity> entities = loadEquipmentSeed().stream()
                    .map(item -> EquipmentEntity.create(
                            item.getBrand().trim(),
                            item.getModel().trim(),
                            EquipmentFunctions.normalize(item.getFunction()),
                            new EquipmentPrice(
                                    item.getPrice().getApproxPriceUsd(),
                                    item.getPrice().getApproxPriceBrl(),
                                    item.getPrice().getScannedAt())))
                    .toList();
            equipmentRepository.persist(entities);
            equipmentCacheService.evictAll();
            LOG.infof("[Seed] %s → %d equipamento(s) inserido(s)", EQUIPMENT_SEED, entities.size());
        } catch (Exception e) {
            LOG.errorf(e, "[Seed] falha ao carregar %s — catálogo de equipamentos vazio neste ambiente", EQUIPMENT_SEED);
        }
    }

    /**
     * Carrega todos os seeds IAM (upsert por {@code (provider, key)}: rodar
     * de novo não duplica e preserva {@code created_at}). O boot só chama
     * com a coleção vazia; o teste chama direto para conferir a idempotência.
     */
    public void seedIam() {
        for (String resource : IAM_SEEDS) {
            try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    LOG.warnf("[Seed] %s não encontrado no classpath", resource);
                    continue;
                }
                CloudIamTemplateBulkUpsertRequest seed = objectMapper.readValue(input, CloudIamTemplateBulkUpsertRequest.class);
                List<CloudIamTemplateEntity> entities = seed.getItems().stream()
                        .map(CloudIamTemplateEntity::fromRequest)
                        .toList();
                int written = iamRepository.bulkUpsert(entities);
                entities.stream().map(CloudIamTemplateEntity::getProvider).distinct().forEach(catalogService::evict);
                LOG.infof("[Seed] %s → %d modelo(s) IAM, %d inserido(s)/alterado(s)", resource, entities.size(), written);
            } catch (Exception e) {
                LOG.errorf(e, "[Seed] falha ao carregar %s — catálogo IAM pode ficar incompleto neste ambiente", resource);
            }
        }
    }
}
