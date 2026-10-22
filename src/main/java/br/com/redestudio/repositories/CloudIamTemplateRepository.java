package br.com.redestudio.repositories;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.entities.CloudIamTemplateEntity;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.entities.IamTemplateKind;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Acesso a {@code cloud_iam_templates} no banco cloud — mesma abordagem de
 * {@link CloudPriceRepository} (MongoClient direto, banco vindo de config,
 * mapeamento explícito em snake_case).
 */
@ApplicationScoped
public class CloudIamTemplateRepository {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "app.cloud-pricing.database")
    String databaseName;

    private MongoCollection<Document> collection() {
        return mongoClient.getDatabase(databaseName).getCollection(CollectionNames.CLOUD_IAM_TEMPLATES);
    }

    /** Idempotente — chamado em todo boot pelo {@code StartupRunner}. */
    public void ensureIndexes() {
        collection().createIndex(
                Indexes.ascending("provider", "key"),
                new IndexOptions().unique(true).name("provider_key_unique"));
        collection().createIndex(Indexes.ascending("provider", "kind", "order"));
    }

    /** Coleção sem nenhum modelo (inexistente conta como vazia) — gatilho do seed de dev. */
    public boolean isEmpty() {
        return collection().countDocuments() == 0;
    }

    /** Todos os modelos de uma família, por tipo e ordem de exibição. */
    public List<CloudIamTemplateEntity> findByProvider(CloudProvider provider) {
        List<CloudIamTemplateEntity> items = new ArrayList<>();
        for (Document doc : collection()
                .find(Filters.eq("provider", provider.name()))
                .sort(Sorts.ascending("kind", "order", "key"))) {
            items.add(fromDocument(doc));
        }
        return items;
    }

    /**
     * Upsert por {@code (provider, key)}: {@code created_at} só no insert,
     * {@code updated_at} em toda gravação.
     *
     * @return documentos inseridos ou modificados
     */
    public int bulkUpsert(List<CloudIamTemplateEntity> items) {
        if (items.isEmpty()) {
            return 0;
        }
        Date now = new Date();
        List<WriteModel<Document>> writes = new ArrayList<>(items.size());
        for (CloudIamTemplateEntity item : items) {
            Bson key = Filters.and(
                    Filters.eq("provider", item.getProvider().name()),
                    Filters.eq("key", item.getKey()));
            List<Bson> sets = new ArrayList<>();
            toFields(item).forEach((field, value) -> sets.add(Updates.set(field, value)));
            sets.add(Updates.set("updated_at", now));
            sets.add(Updates.setOnInsert("created_at", now));
            writes.add(new UpdateOneModel<>(key, Updates.combine(sets), new UpdateOptions().upsert(true)));
        }
        var result = collection().bulkWrite(writes, new BulkWriteOptions().ordered(false));
        return result.getUpserts().size() + result.getModifiedCount();
    }

    private static Document toFields(CloudIamTemplateEntity item) {
        return new Document()
                .append("kind", item.getKind().name())
                .append("name", item.getName() != null ? new Document(item.getName()) : new Document())
                .append("description", item.getDescription() != null ? new Document(item.getDescription()) : new Document())
                .append("iam_name", item.getIamName())
                .append("policies", item.getPolicies() != null ? item.getPolicies() : List.of())
                .append("trusted_service", item.getTrustedService())
                .append("policy_arn", item.getPolicyArn())
                .append("order", item.getOrder() != null ? item.getOrder() : 0);
    }

    @SuppressWarnings("unchecked")
    private static CloudIamTemplateEntity fromDocument(Document doc) {
        CloudIamTemplateEntity item = new CloudIamTemplateEntity();
        item.setId(doc.getObjectId("_id").toHexString());
        item.setProvider(CloudProvider.valueOf(doc.getString("provider")));
        item.setKind(IamTemplateKind.valueOf(doc.getString("kind")));
        item.setKey(doc.getString("key"));
        item.setName(localized(doc.get("name")));
        item.setDescription(localized(doc.get("description")));
        item.setIamName(doc.getString("iam_name"));
        Object policies = doc.get("policies");
        item.setPolicies(policies instanceof List ? (List<String>) policies : List.of());
        item.setTrustedService(doc.getString("trusted_service"));
        item.setPolicyArn(doc.getString("policy_arn"));
        item.setOrder(doc.getInteger("order", 0));
        item.setCreatedAt(CloudPriceRepository.instant(doc.get("created_at")));
        item.setUpdatedAt(CloudPriceRepository.instant(doc.get("updated_at")));
        return item;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> localized(Object value) {
        return value instanceof Map ? Map.copyOf((Map<String, String>) value) : Map.of();
    }
}
