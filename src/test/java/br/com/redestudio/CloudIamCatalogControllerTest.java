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
import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Date;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for {@code CloudCatalogController} (cloud-l0-conta-iam,
 * fase 2): ADMIN bulk upsert of IAM templates through the queue, the
 * grouped read, the created_at/updated_at contract and cache eviction.
 *
 * <p>Uses provider {@code OCI} with keys {@code oci.test.*} — never touches
 * the real AWS catalog — and removes everything it creates.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class CloudIamCatalogControllerTest {

    @Inject
    MongoClient mongoClient;

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordHasher passwordHasher;

    @ConfigProperty(name = "app.cloud-pricing.database")
    String cloudDatabase;

    private static final String ADMIN_EMAIL = "iamtest-admin@test.local";
    private static final String USER_EMAIL = "iamtest-user@test.local";
    private static final String PASSWORD = "Password@Test1";
    private static final Bson TEST_DOCS = Filters.regex("key", "^oci\\.test\\.");

    private static String adminToken;
    private static String userToken;
    private static Date firstCreatedAt;
    private static Date firstUpdatedAt;

    private MongoCollection<Document> templates() {
        return mongoClient.getDatabase(cloudDatabase).getCollection(CollectionNames.CLOUD_IAM_TEMPLATES);
    }

    private static String items(String groupName) {
        return """
                { "items": [
                  { "provider": "OCI", "kind": "POLICY", "key": "oci.test.policy.read",
                    "name": { "pt": "Leitura", "en": "Read", "es": "Lectura" },
                    "policyArn": "ocid1.policy.test", "order": 1 },
                  { "provider": "OCI", "kind": "GROUP", "key": "oci.test.group.readers",
                    "name": { "pt": "%s", "en": "Readers", "es": "Lectores" },
                    "iamName": "Readers", "policies": ["oci.test.policy.read"], "order": 1 },
                  { "provider": "OCI", "kind": "ROLE", "key": "oci.test.role.compute",
                    "name": { "pt": "Compute", "en": "Compute", "es": "Compute" },
                    "iamName": "compute-role", "trustedService": "compute.oraclecloud.com",
                    "policies": ["oci.test.policy.read"], "order": 1 }
                ] }
                """.formatted(groupName);
    }

    @Test
    @Order(1)
    void setup_obtainAdminAndUserTokens() {
        templates().deleteMany(TEST_DOCS);
        userRepository.deleteByEmail(ADMIN_EMAIL);
        userRepository.persist(UserEntity.create(
                "iamtestadmin", ADMIN_EMAIL, passwordHasher.hash(PASSWORD), Set.of("USER", "ADMIN")));
        userRepository.deleteByEmail(USER_EMAIL);
        userRepository.persist(UserEntity.create(
                "iamtestuser", USER_EMAIL, passwordHasher.hash(PASSWORD), Set.of("USER")));
        adminToken = login(ADMIN_EMAIL);
        userToken = login(USER_EMAIL);
    }

    @Test
    @Order(2)
    void bulkUpsert_asUser_returns403_invalidKey_returns400() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(items("Leitores"))
        .when()
                .post("/api/cloud-catalog/iam/bulk-upsert")
        .then()
                .statusCode(403);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        { "items": [ { "provider": "OCI", "kind": "GROUP", "key": "Invalid Key!",
                          "name": { "pt": "x" } } ] }
                        """)
        .when()
                .post("/api/cloud-catalog/iam/bulk-upsert")
        .then()
                .statusCode(400);
    }

    @Test
    @Order(3)
    void bulkUpsert_asAdmin_isPersistedAndGroupedByKind() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(items("Leitores"))
        .when()
                .post("/api/cloud-catalog/iam/bulk-upsert")
        .then()
                .statusCode(202)
                .body("received", equalTo(3));

        waitFor(() -> templates().countDocuments(TEST_DOCS) == 3);
        Document group = templates().find(Filters.eq("key", "oci.test.group.readers")).first();
        firstCreatedAt = group.getDate("created_at");
        firstUpdatedAt = group.getDate("updated_at");
        assertEquals(firstCreatedAt, firstUpdatedAt);

        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/cloud-catalog/iam?provider=OCI")
        .then()
                .statusCode(200)
                .body("provider", equalTo("OCI"))
                .body("groups.key", hasItem("oci.test.group.readers"))
                .body("groups.find { it.key == 'oci.test.group.readers' }.policies", hasItem("oci.test.policy.read"))
                .body("roles.find { it.key == 'oci.test.role.compute' }.trustedService", equalTo("compute.oraclecloud.com"))
                .body("policies.find { it.key == 'oci.test.policy.read' }.policyArn", equalTo("ocid1.policy.test"));
    }

    @Test
    @Order(4)
    void bulkUpsert_again_keepsCreatedAt_andEvictsCache() throws InterruptedException {
        Thread.sleep(20);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(items("Leitores renomeados"))
        .when()
                .post("/api/cloud-catalog/iam/bulk-upsert")
        .then()
                .statusCode(202);

        waitFor(() -> {
            Document doc = templates().find(Filters.eq("key", "oci.test.group.readers")).first();
            return doc != null && doc.getDate("updated_at").after(firstUpdatedAt);
        });
        Document group = templates().find(Filters.eq("key", "oci.test.group.readers")).first();
        assertEquals(firstCreatedAt, group.getDate("created_at"), "created_at must survive a reload");
        assertEquals(3, templates().countDocuments(TEST_DOCS), "reload must not duplicate");

        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/cloud-catalog/iam?provider=OCI")
        .then()
                .statusCode(200)
                .body("groups.find { it.key == 'oci.test.group.readers' }.name.pt", equalTo("Leitores renomeados"));
    }

    @Test
    @Order(5)
    void cleanup_removeTestData() {
        templates().deleteMany(TEST_DOCS);
        userRepository.deleteByEmail(ADMIN_EMAIL);
        userRepository.deleteByEmail(USER_EMAIL);
    }

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
