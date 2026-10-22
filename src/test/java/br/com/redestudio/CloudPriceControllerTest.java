package br.com.redestudio;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.components.PasswordHasher;
import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.repositories.UserRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for {@code CloudPriceController} (sprint-cloud-aws, B2/B3):
 * ADMIN bulk upsert through the queue, catalog/meta/search reads, the
 * created_at/updated_at upsert contract and cache eviction on reload.
 *
 * <p>Uses fake regions ({@code zz-test-*}) and removes every document and
 * user it creates — the test databases may persist between runs (see the
 * note in {@code ProjectCloudControllerTest}).
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class CloudPriceControllerTest {

    @Inject
    MongoClient mongoClient;

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordHasher passwordHasher;

    @ConfigProperty(name = "app.cloud-pricing.database")
    String cloudDatabase;

    private static final String ADMIN_EMAIL = "cptest-admin@test.local";
    private static final String USER_EMAIL = "cptest-user@test.local";
    private static final String PASSWORD = "Password@Test1";
    private static final String REGION_A = "zz-test-1";
    private static final String REGION_B = "zz-test-2";

    private static String adminToken;
    private static String userToken;
    private static Date firstCreatedAt;
    private static Date firstUpdatedAt;

    private MongoCollection<Document> prices() {
        return mongoClient.getDatabase(cloudDatabase).getCollection(CollectionNames.CLOUD_PRICES);
    }

    private static String item(String region, String rateCode, String instanceType, String usd, String brl) {
        return """
                {
                    "provider": "AWS",
                    "serviceCode": "AmazonEC2",
                    "resourceType": "aws.ec2.instance",
                    "region": "%s",
                    "regionName": "Test Region %s",
                    "sku": "SKU-%s",
                    "rateCode": "%s",
                    "description": "%s Linux On-Demand",
                    "attributes": { "instanceType": "%s", "vcpu": 2, "operatingSystem": "Linux" },
                    "unit": "Hrs",
                    "price": { "usd": %s, "brl": %s, "fxRate": 5.5, "fxQuotedAt": "2026-10-16T00:00:00Z" },
                    "pricingModel": "ON_DEMAND",
                    "effectiveDate": "2026-10-01T00:00:00Z",
                    "source": { "offerCode": "AmazonEC2", "publicationVersion": "test", "url": "https://example.test" }
                }
                """.formatted(region, region, rateCode, rateCode, instanceType, instanceType, usd, brl);
    }

    @Test
    @Order(1)
    void setup_obtainAdminAndUserTokens() {
        userRepository.deleteByEmail(ADMIN_EMAIL);
        userRepository.persist(UserEntity.create(
                "cptestadmin", ADMIN_EMAIL, passwordHasher.hash(PASSWORD), Set.of("USER", "ADMIN")));
        userRepository.deleteByEmail(USER_EMAIL);
        userRepository.persist(UserEntity.create(
                "cptestuser", USER_EMAIL, passwordHasher.hash(PASSWORD), Set.of("USER")));
        prices().deleteMany(Filters.regex("region", "^zz-test-"));

        adminToken = login(ADMIN_EMAIL);
        userToken = login(USER_EMAIL);
    }

    @Test
    @Order(2)
    void bulkUpsert_asUser_returns403() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("{ \"items\": [ %s ] }".formatted(item(REGION_A, "RC-T3MICRO", "t3.micro", "0.0168", "0.0924")))
        .when()
                .post("/api/cloud-prices/bulk-upsert")
        .then()
                .statusCode(403);
    }

    @Test
    @Order(3)
    void bulkUpsert_invalidItem_returns400() {
        // sem price
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        { "items": [ { "provider": "AWS", "serviceCode": "AmazonEC2", "resourceType": "aws.ec2.instance",
                          "region": "zz-test-1", "sku": "x", "rateCode": "x", "unit": "Hrs", "pricingModel": "ON_DEMAND" } ] }
                        """)
        .when()
                .post("/api/cloud-prices/bulk-upsert")
        .then()
                .statusCode(400);
    }

    @Test
    @Order(4)
    void bulkUpsert_asAdmin_isPersistedAndReadable() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{ \"items\": [ %s, %s, %s ] }".formatted(
                        item(REGION_A, "RC-T3MICRO", "t3.micro", "0.0168", "0.0924"),
                        item(REGION_A, "RC-T3SMALL", "t3.small", "0.0336", "0.1848"),
                        item(REGION_B, "RC-B-T3MICRO", "t3.micro", "0.0104", "0.0572")))
        .when()
                .post("/api/cloud-prices/bulk-upsert")
        .then()
                .statusCode(202)
                .body("received", equalTo(3))
                .body("batches", equalTo(1));

        waitFor(() -> prices().countDocuments(Filters.regex("region", "^zz-test-")) == 3);

        Document doc = prices().find(Filters.eq("rate_code", "RC-T3MICRO")).first();
        Document price = doc.get("price", Document.class);
        assertEquals(new BigDecimal("0.0168"), ((Decimal128) price.get("usd")).bigDecimalValue());
        assertEquals(new BigDecimal("0.0924"), ((Decimal128) price.get("brl")).bigDecimalValue());
        assertEquals("AWS", doc.getString("provider"));
        firstCreatedAt = doc.getDate("created_at");
        firstUpdatedAt = doc.getDate("updated_at");
        assertEquals(firstCreatedAt, firstUpdatedAt, "first insert: created_at == updated_at");

        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/cloud-prices/catalog?provider=AWS&region=" + REGION_A)
        .then()
                .statusCode(200)
                .body("region", equalTo(REGION_A))
                .body("regionName", equalTo("Test Region " + REGION_A))
                .body("items.size()", equalTo(2))
                .body("items.find { it.sku == 'SKU-RC-T3MICRO' }.usd", equalTo(0.0168f))
                .body("items.find { it.sku == 'SKU-RC-T3MICRO' }.brl", equalTo(0.0924f))
                .body("fxRate", equalTo(5.5f));

        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/cloud-prices/meta")
        .then()
                .statusCode(200)
                .body("regions.region", hasItem(REGION_A))
                .body("regions.region", hasItem(REGION_B))
                .body("regions.find { it.region == '" + REGION_A + "' }.itemCount", equalTo(2));
    }

    @Test
    @Order(5)
    void bulkUpsert_again_updatesPriceKeepsCreatedAt_andEvictsCache() throws InterruptedException {
        Thread.sleep(20); // garante updated_at estritamente maior

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{ \"items\": [ %s ] }".formatted(item(REGION_A, "RC-T3MICRO", "t3.micro", "0.0200", "0.1100")))
        .when()
                .post("/api/cloud-prices/bulk-upsert")
        .then()
                .statusCode(202);

        waitFor(() -> {
            Document doc = prices().find(Filters.eq("rate_code", "RC-T3MICRO")).first();
            return doc != null && doc.getDate("updated_at").after(firstUpdatedAt);
        });

        Document doc = prices().find(Filters.eq("rate_code", "RC-T3MICRO")).first();
        assertEquals(firstCreatedAt, doc.getDate("created_at"), "created_at must survive a reload");
        assertTrue(doc.getDate("updated_at").after(firstCreatedAt));
        assertEquals(3, prices().countDocuments(Filters.regex("region", "^zz-test-")), "reload must not duplicate");

        // O catálogo da região A tinha sido cacheado no passo anterior — a
        // recarga precisa invalidar, senão o preço antigo ficaria 24h no Redis.
        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/cloud-prices/catalog?provider=AWS&region=" + REGION_A)
        .then()
                .statusCode(200)
                .body("items.find { it.sku == 'SKU-RC-T3MICRO' }.usd", equalTo(0.02f));
    }

    @Test
    @Order(6)
    void search_byResourceType_returnsFullEntries() {
        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/cloud-prices?provider=AWS&region=" + REGION_B + "&resourceType=aws.ec2.instance")
        .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].attributes.instanceType", equalTo("t3.micro"))
                .body("[0].price.fxRate", equalTo(5.5f));
    }

    @Test
    @Order(7)
    void catalog_unknownRegion_returnsEmpty_invalidRegion_returns400() {
        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/cloud-prices/catalog?provider=AWS&region=zz-test-empty")
        .then()
                .statusCode(200)
                .body("items.size()", equalTo(0));

        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/cloud-prices/catalog?provider=AWS&region=INVALID_REGION!")
        .then()
                .statusCode(400);
    }

    @Test
    @Order(8)
    void cleanup_removeTestData() {
        prices().deleteMany(Filters.regex("region", "^zz-test-"));
        userRepository.deleteByEmail(ADMIN_EMAIL);
        userRepository.deleteByEmail(USER_EMAIL);
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private static String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD))
        .when()
                .post("/api/auth/login")
        .then()
                .statusCode(200)
                .extract().path("token");
    }

    /** Polls until {@code condition} holds (async consumer), failing after ~10s. */
    private static void waitFor(BooleanSupplier condition) {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for async persistence");
            }
        }
        fail("Condition not met within timeout (async consumer did not persist in time)");
    }
}
