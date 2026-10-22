package br.com.redestudio;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.dtos.request.CreateEquipmentRequest;
import br.com.redestudio.runners.DevSeedRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seeds de dev ({@link DevSeedRunner}): fora do {@code %dev} a flag é falsa;
 * o seed IAM carrega o arquivo empacotado e é idempotente (rodar de novo não
 * duplica e preserva {@code created_at}); o seed de equipamentos é válido
 * no formato do bulk e já vem com {@code function} em lista.
 *
 * <p>Diferente dos outros testes, não apaga o que grava: o resultado é
 * exatamente o catálogo AWS que o ambiente de dev deve ter.
 */
@QuarkusTest
class DevSeedRunnerTest {

    @Inject
    DevSeedRunner seedRunner;

    @Inject
    MongoClient mongoClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.cloud-pricing.database")
    String cloudDatabase;

    @ConfigProperty(name = "app.dev-seed.enabled")
    boolean devSeedEnabled;

    @Test
    void iamSeed_isOffOutsideDev_loadsPackagedCatalog_andIsIdempotent() throws Exception {
        assertFalse(devSeedEnabled, "dev-seed must be off outside %dev");

        int expected;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("seed/cloud-catalog/aws-iam.json")) {
            JsonNode items = objectMapper.readTree(input).get("items");
            expected = items.size();
        }
        MongoCollection<Document> templates =
                mongoClient.getDatabase(cloudDatabase).getCollection(CollectionNames.CLOUD_IAM_TEMPLATES);

        seedRunner.seedIam();
        assertEquals(expected, templates.countDocuments(Filters.eq("provider", "AWS")));
        Document group = templates.find(Filters.eq("key", "aws.group.network-admins")).first();
        Date createdAt = group.getDate("created_at");
        Date updatedAt = group.getDate("updated_at");

        Thread.sleep(20);
        seedRunner.seedIam();
        assertEquals(expected, templates.countDocuments(Filters.eq("provider", "AWS")), "re-seed must not duplicate");
        Document again = templates.find(Filters.eq("key", "aws.group.network-admins")).first();
        assertEquals(createdAt, again.getDate("created_at"), "created_at must survive a re-seed");
        assertTrue(again.getDate("updated_at").after(updatedAt), "updated_at must advance");
    }

    @Test
    void equipmentSeed_isValidBulkPayload_withFunctionLists() throws Exception {
        List<CreateEquipmentRequest> items = seedRunner.loadEquipmentSeed();
        assertFalse(items.isEmpty());
        for (CreateEquipmentRequest item : items) {
            assertFalse(item.getBrand() == null || item.getBrand().isBlank(), "brand");
            assertFalse(item.getModel() == null || item.getModel().isBlank(), "model");
            assertFalse(item.getFunction() == null || item.getFunction().isEmpty(), item.getModel() + " function");
            assertTrue(item.getPrice().getApproxPriceUsd().signum() > 0, item.getModel() + " usd");
            assertTrue(item.getPrice().getApproxPriceBrl().signum() > 0, item.getModel() + " brl");
            assertTrue(item.getPrice().getScannedAt() != null, item.getModel() + " scannedAt");
        }
    }
}
