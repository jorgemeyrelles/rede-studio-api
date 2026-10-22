package br.com.redestudio;

import br.com.redestudio.entities.CloudStateEntity;
import br.com.redestudio.entities.ProjectEntity;
import br.com.redestudio.repositories.CloudStateRepository;
import br.com.redestudio.repositories.ProjectRepository;
import br.com.redestudio.repositories.UserRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for the {@code CLOUD} project path (sprint-cloud-aws,
 * fase B1): create with a {@code cloudState}, autosave via
 * {@code /cloud-snapshot}, type-mismatch 409s, owner isolation and delete.
 *
 * <p>Same stack as the other {@code @QuarkusTest}s (Redis + RabbitMQ +
 * MongoDB, no mocks). Mongo-side assertions poll ({@link #waitFor}) because
 * {@code ProjectMutationConsumer} persists asynchronously.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class ProjectCloudControllerTest {

    @Inject
    ProjectRepository projectRepository;

    @Inject
    CloudStateRepository cloudStateRepository;

    @Inject
    UserRepository userRepository;

    private static final String OWNER_EMAIL = "cloudtest-owner@test.local";
    private static final String OTHER_EMAIL = "cloudtest-other@test.local";

    private static String ownerToken;
    private static String otherToken;
    private static String cloudProjectId;
    private static String onPremProjectId;

    private static final String EMPTY_CLOUD_STATE = """
            {
                "schemaVersion": 1,
                "provider": "AWS",
                "resources": [],
                "connections": [],
                "assumptions": { "currency": "USD", "hoursPerMonth": 730 },
                "ui": {},
                "meta": {}
            }
            """;

    private static final String CLOUD_STATE_WITH_ACCOUNT = """
            {
                "schemaVersion": 1,
                "provider": "AWS",
                "resources": [ { "id": "acc-1", "type": "aws.account", "parentId": null, "name": "Conta" } ],
                "connections": [],
                "assumptions": { "currency": "BRL", "hoursPerMonth": 730 },
                "ui": {},
                "meta": {}
            }
            """;

    private static final String EMPTY_NETWORK_STATE = """
            {
                "sites": [], "layers": [], "nodes": [], "links": [], "aclRules": [],
                "siteVlans": [], "siteNetworks": [], "subnets": [], "nodeVlanInterfaces": [],
                "dhcpScopes": [], "nodeQosProfiles": [], "customServices": [], "certificates": [],
                "ipsecSas": [], "sslVpnProfiles": [], "fwPolicies": [], "natRules": [],
                "activeSessions": [], "counters": {}, "ui": {}, "meta": {}
            }
            """;

    @Test
    @Order(1)
    void setup_registerTwoUsers() {
        ownerToken = register("cloudtestowner", OWNER_EMAIL);
        otherToken = register("cloudtestother", OTHER_EMAIL);
    }

    @Test
    @Order(2)
    void create_cloudProject_returnsCloudStateOnly() {
        cloudProjectId = given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("""
                        { "name": "CLOUDTEST VPC", "networkType": "CLOUD", "cloudState": %s }
                        """.formatted(EMPTY_CLOUD_STATE))
        .when()
                .post("/api/projects")
        .then()
                .statusCode(201)
                .body("networkType", equalTo("CLOUD"))
                .body("cloudState.provider", equalTo("AWS"))
                .body("networkState", nullValue())
                .extract().path("id");

        waitFor(() -> projectRepository.findById(new ObjectId(cloudProjectId)) != null);
        ProjectEntity project = projectRepository.findById(new ObjectId(cloudProjectId));
        assertNull(project.getNetworkStateId(), "CLOUD project must not reference a network_states document");
        waitFor(() -> cloudStateRepository.findById(project.getCloudStateId()) != null);
    }

    @Test
    @Order(3)
    void create_rejectsStateThatDoesNotMatchNetworkType() {
        // CLOUD com networkState
        postProject("""
                { "name": "x", "networkType": "CLOUD", "networkState": %s }
                """.formatted(EMPTY_NETWORK_STATE), 400);
        // CLOUD com os dois
        postProject("""
                { "name": "x", "networkType": "CLOUD", "networkState": %s, "cloudState": %s }
                """.formatted(EMPTY_NETWORK_STATE, EMPTY_CLOUD_STATE), 400);
        // ON_PREMISE só com cloudState
        postProject("""
                { "name": "x", "networkType": "ON_PREMISE", "cloudState": %s }
                """.formatted(EMPTY_CLOUD_STATE), 400);
        // cloudState sem chave obrigatória (resources)
        postProject("""
                { "name": "x", "networkType": "CLOUD", "cloudState":
                  { "schemaVersion": 1, "provider": "AWS", "connections": [], "assumptions": {}, "ui": {}, "meta": {} } }
                """, 400);
    }

    @Test
    @Order(4)
    void cloudSnapshot_updatesDraft_andIsReadBack() {
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("{ \"cloudState\": %s }".formatted(CLOUD_STATE_WITH_ACCOUNT))
        .when()
                .put("/api/projects/" + cloudProjectId + "/cloud-snapshot")
        .then()
                .statusCode(200)
                .body("id", equalTo(cloudProjectId));

        given()
                .header("Authorization", "Bearer " + ownerToken)
        .when()
                .get("/api/projects/" + cloudProjectId)
        .then()
                .statusCode(200)
                .body("cloudState.resources[0].type", equalTo("aws.account"))
                .body("cloudState.assumptions.currency", equalTo("BRL"));

        ProjectEntity project = projectRepository.findById(new ObjectId(cloudProjectId));
        waitFor(() -> {
            CloudStateEntity draft = cloudStateRepository.findById(project.getCloudStateId());
            return draft != null && draft.getResources().size() == 1;
        });
    }

    @Test
    @Order(5)
    void onPremSnapshot_onCloudProject_returns409() {
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("{ \"networkState\": %s }".formatted(EMPTY_NETWORK_STATE))
        .when()
                .put("/api/projects/" + cloudProjectId + "/snapshot")
        .then()
                .statusCode(409);
    }

    @Test
    @Order(6)
    void cloudSnapshot_onOnPremProject_returns409() {
        onPremProjectId = given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("""
                        { "name": "CLOUDTEST on-prem", "networkType": "ON_PREMISE", "networkState": %s }
                        """.formatted(EMPTY_NETWORK_STATE))
        .when()
                .post("/api/projects")
        .then()
                .statusCode(201)
                .body("cloudState", nullValue())
                .body("networkState", notNullValue())
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("{ \"cloudState\": %s }".formatted(EMPTY_CLOUD_STATE))
        .when()
                .put("/api/projects/" + onPremProjectId + "/cloud-snapshot")
        .then()
                .statusCode(409);
    }

    @Test
    @Order(7)
    void otherUser_cannotReadOrWriteCloudProject() {
        given()
                .header("Authorization", "Bearer " + otherToken)
        .when()
                .get("/api/projects/" + cloudProjectId)
        .then()
                .statusCode(404);

        given()
                .header("Authorization", "Bearer " + otherToken)
                .contentType(ContentType.JSON)
                .body("{ \"cloudState\": %s }".formatted(EMPTY_CLOUD_STATE))
        .when()
                .put("/api/projects/" + cloudProjectId + "/cloud-snapshot")
        .then()
                .statusCode(404);
    }

    @Test
    @Order(8)
    void delete_cloudProject_removesDraft() {
        ObjectId cloudStateId = projectRepository.findById(new ObjectId(cloudProjectId)).getCloudStateId();

        given()
                .header("Authorization", "Bearer " + ownerToken)
        .when()
                .delete("/api/projects/" + cloudProjectId)
        .then()
                .statusCode(204);

        waitFor(() -> projectRepository.findById(new ObjectId(cloudProjectId)) == null
                && cloudStateRepository.findById(cloudStateId) == null);

        given()
                .header("Authorization", "Bearer " + ownerToken)
        .when()
                .delete("/api/projects/" + onPremProjectId)
        .then()
                .statusCode(204);

        // Espera o consumer: sem isso o Quarkus pode encerrar antes de a fila
        // processar o delete e deixar o projeto órfão no banco.
        waitFor(() -> projectRepository.findById(new ObjectId(onPremProjectId)) == null);
    }

    @Test
    @Order(9)
    void cleanup_removeTestUsers() {
        // Sem Dev Services (Mongo explícito no application.yml), o banco de
        // teste persiste entre execuções — remove os usuários criados aqui
        // para a próxima execução conseguir registrá-los de novo.
        userRepository.deleteByEmail(OWNER_EMAIL);
        userRepository.deleteByEmail(OTHER_EMAIL);
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private static String register(String username, String email) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        { "username": "%s", "email": "%s", "password": "Password@Test1" }
                        """.formatted(username, email))
        .when()
                .post("/api/auth/register")
        .then()
                .statusCode(201)
                .extract().path("token");
    }

    private static void postProject(String body, int expectedStatus) {
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/api/projects")
        .then()
                .statusCode(expectedStatus);
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
