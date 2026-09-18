package br.com.redestudio;

import br.com.redestudio.components.PasswordHasher;
import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.repositories.EquipmentRepository;
import br.com.redestudio.repositories.UserRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Set;

import java.util.List;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for {@code EquipmentController} — exercises the full
 * HTTP stack: Quarkus HTTP → JwtAuthenticationFilter → EquipmentController
 * → EquipmentService → Redis/RabbitMQ → MongoDB.
 *
 * <p>MongoDB is provided by Quarkus Dev Services (Testcontainers) — Docker
 * must be running. No mocks are used. Reads/writes routed through
 * {@code EquipmentService#listAll}/{@code #getById} are backed by Redis and
 * are consistent immediately after a write; {@code listByBrand}/
 * {@code searchByModelContains} read straight from Mongo (see
 * {@code EquipmentService}'s class Javadoc) and therefore race
 * {@code EquipmentMutationConsumer} — those assertions poll, mirroring the
 * {@code waitFor} pattern already used by {@code AuthControllerTest} for
 * the same reason (async registration).
 *
 * <p>Uses a unique brand prefix ({@link #BRAND}) to avoid collisions with
 * any other data in the shared test database.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class EquipmentControllerTest {

    @Inject
    EquipmentRepository equipmentRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordHasher passwordHasher;

    private static final String ADMIN_EMAIL = "eqtest-admin@test.local";
    private static final String ADMIN_PASSWORD = "Password@Test1";

    private static final String BRAND = "EQTEST-Brand";
    private static final String MODEL_ONE = "EQTEST-Model-Router-3000";
    private static final String MODEL_TWO = "EQTEST-Model-Switch-9000";
    private static final String MODEL_BULK = "EQTEST-Bulk-AP-500";

    private static String adminToken;
    private static String userToken;
    private static String createdId;
    private static String bulkCreatedId;

    // -----------------------------------------------------------------------
    //  Auth setup — reused by every test below (ADMIN vs plain USER token)
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    void setup_obtainAdminAndUserTokens() {
        // Cria um ADMIN dedicado a este teste direto via repositório — não
        // depende do admin default provisionado por StartupRunner
        // (app.admin.*), cujo valor efetivo varia conforme a config ativa
        // no ambiente (base vs. src/test/resources/application.properties);
        // criar aqui garante credenciais conhecidas e isola este teste de
        // qualquer outro que também mexa no usuário admin.
        UserEntity admin = UserEntity.create(
                "eqtestadmin", ADMIN_EMAIL, passwordHasher.hash(ADMIN_PASSWORD), Set.of("USER", "ADMIN"));
        userRepository.persist(admin);

        adminToken = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD))
        .when()
                .post("/api/auth/login")
        .then()
                .statusCode(200)
                .extract().path("token");

        userToken = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "username": "eqtestuser",
                            "email": "eqtest-user@test.local",
                            "password": "Password@Test1"
                        }
                        """)
        .when()
                .post("/api/auth/register")
        .then()
                .statusCode(201)
                .extract().path("token");
    }

    // -----------------------------------------------------------------------
    //  Create
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    void create_asAdmin_returns201() {
        createdId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "brand": "%s",
                            "model": "%s",
                            "function": "router",
                            "price": {
                                "approxPriceUsd": 4697.80,
                                "approxPriceBrl": 24203.67,
                                "scannedAt": "2026-09-16T00:00:00Z"
                            }
                        }
                        """.formatted(BRAND, MODEL_ONE))
        .when()
                .post("/api/equipments")
        .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("brand", equalTo(BRAND))
                .body("model", equalTo(MODEL_ONE))
                .body("function", equalTo("router"))
                .body("price.approxPriceUsd", equalTo(4697.80f))
                .extract().path("id");
    }

    @Test
    @Order(3)
    void create_asUser_returns403() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "brand": "%s",
                            "model": "should-not-be-created",
                            "function": "router",
                            "price": { "approxPriceUsd": 1.0, "approxPriceBrl": 5.0, "scannedAt": "2026-09-16T00:00:00Z" }
                        }
                        """.formatted(BRAND))
        .when()
                .post("/api/equipments")
        .then()
                .statusCode(403);
    }

    @Test
    @Order(4)
    void create_withoutAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "brand": "%s",
                            "model": "no-auth-model",
                            "function": "router",
                            "price": { "approxPriceUsd": 1.0, "approxPriceBrl": 5.0, "scannedAt": "2026-09-16T00:00:00Z" }
                        }
                        """.formatted(BRAND))
        .when()
                .post("/api/equipments")
        .then()
                .statusCode(401);
    }

    // -----------------------------------------------------------------------
    //  Bulk create
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    void bulkCreate_asAdmin_returns201WithBothItems() {
        List<Object> ids = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "items": [
                                {
                                    "brand": "%s",
                                    "model": "%s",
                                    "function": "switch",
                                    "price": { "approxPriceUsd": 194.00, "approxPriceBrl": 999.10, "scannedAt": "2026-09-16T00:00:00Z" }
                                },
                                {
                                    "brand": "%s",
                                    "model": "%s",
                                    "function": "access point",
                                    "price": { "approxPriceUsd": 149.99, "approxPriceBrl": 772.45, "scannedAt": "2026-09-16T00:00:00Z" }
                                }
                            ]
                        }
                        """.formatted(BRAND, MODEL_TWO, BRAND, MODEL_BULK))
        .when()
                .post("/api/equipments/bulk")
        .then()
                .statusCode(201)
                .body("size()", equalTo(2))
                .extract().path("id");

        bulkCreatedId = (String) ids.get(0);
    }

    // -----------------------------------------------------------------------
    //  List / filter / search
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    void listAll_includesCreatedItems() {
        // listAll é servido pelo cache Redis, sincronamente consistente logo
        // após os creates acima (sem corrida com o consumer) — ver Javadoc
        // de EquipmentService.
        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/equipments")
        .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(3))
                .body("model", org.hamcrest.Matchers.hasItems(MODEL_ONE, MODEL_TWO, MODEL_BULK));
    }

    @Test
    @Order(7)
    void listByBrand_returnsAllEquipmentTestItems() {
        // listByBrand lê direto do Mongo — espera o consumer assíncrono
        // aplicar os creates/bulk-create acima antes de afirmar.
        waitForCount(() -> equipmentRepository.listByBrand(BRAND).size(), 3);

        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/equipments/brand/" + BRAND)
        .then()
                .statusCode(200)
                .body("size()", equalTo(3))
                .body("brand", org.hamcrest.Matchers.everyItem(equalTo(BRAND)));
    }

    @Test
    @Order(8)
    void searchByName_partialModel_returnsMatchingItems() {
        waitForCount(() -> equipmentRepository.findByModelContains("Model-Router").size(), 1);

        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/equipments/search?name=Model-Router")
        .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].model", equalTo(MODEL_ONE));
    }

    // -----------------------------------------------------------------------
    //  Get by id
    // -----------------------------------------------------------------------

    @Test
    @Order(9)
    void getById_returns200() {
        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/equipments/" + createdId)
        .then()
                .statusCode(200)
                .body("id", equalTo(createdId))
                .body("model", equalTo(MODEL_ONE));
    }

    @Test
    @Order(10)
    void getById_unknownId_returns404() {
        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/equipments/000000000000000000000000")
        .then()
                .statusCode(404);
    }

    // -----------------------------------------------------------------------
    //  Update
    // -----------------------------------------------------------------------

    @Test
    @Order(11)
    void update_asAdmin_changesOnlyGivenFields() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "function": "roteador de borda"
                        }
                        """)
        .when()
                .patch("/api/equipments/" + createdId)
        .then()
                .statusCode(200)
                .body("id", equalTo(createdId))
                .body("brand", equalTo(BRAND))
                .body("model", equalTo(MODEL_ONE))
                .body("function", equalTo("roteador de borda"));
    }

    @Test
    @Order(12)
    void update_asUser_returns403() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        { "function": "should-not-apply" }
                        """)
        .when()
                .patch("/api/equipments/" + createdId)
        .then()
                .statusCode(403);
    }

    // -----------------------------------------------------------------------
    //  Delete
    // -----------------------------------------------------------------------

    @Test
    @Order(13)
    void delete_asUser_returns403() {
        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .delete("/api/equipments/" + bulkCreatedId)
        .then()
                .statusCode(403);
    }

    @Test
    @Order(14)
    void delete_asAdmin_returns204ThenGetReturns404() {
        given()
                .header("Authorization", "Bearer " + adminToken)
        .when()
                .delete("/api/equipments/" + bulkCreatedId)
        .then()
                .statusCode(204);

        // A resposta 204 só garante que o cache Redis já refletiu a remoção
        // — GET /{id} cai no Mongo em caso de miss de cache, e um segundo GET
        // rápido demais pode ainda achar o documento lá (consumer assíncrono
        // não aplicou o delete ainda) e re-popular o cache com ele, mascarando
        // a exclusão. Espera o delete assíncrono chegar no Mongo antes de
        // afirmar 404 — mesma corrida documentada para listByBrand/search.
        waitForAbsence(bulkCreatedId);

        given()
                .header("Authorization", "Bearer " + userToken)
        .when()
                .get("/api/equipments/" + bulkCreatedId)
        .then()
                .statusCode(404);
    }

    /**
     * Polls {@code countSupplier} until it reaches at least {@code expected}.
     *
     * <p>15s (not the 3s used by {@code AuthControllerTest#waitFor}) because
     * the RabbitMQ client's own connect/retry backoff — observed locally
     * against the Dev Services broker — can itself take several seconds
     * before {@code EquipmentMutationConsumer} even starts receiving.
     */
    private static void waitForCount(Supplier<Integer> countSupplier, int expected) {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (countSupplier.get() >= expected) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        fail("Expected count of at least " + expected + " was not reached within 15s of the async mutation path");
    }

    /** Polls the repository until {@code id} is gone from Mongo, up to 15s. */
    private void waitForAbsence(String id) {
        org.bson.types.ObjectId objectId = new org.bson.types.ObjectId(id);
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (equipmentRepository.findByIdOptional(objectId).isEmpty()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        fail("Equipment " + id + " was still present in Mongo 15s after the async delete path");
    }
}
