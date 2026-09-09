package br.com.redestudio.runners;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.components.PasswordHasher;
import br.com.redestudio.configurations.AdminConfiguration;
import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.repositories.UserRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Application startup hook responsible for:
 * <ol>
 *   <li>Logging the active profile and database name for observability.</li>
 *   <li>Creating unique MongoDB indexes on {@code users.email} and
 *       {@code users.username} (idempotent — safe to run on every boot).</li>
 *   <li>Provisioning a default admin user on first run if it does not
 *       already exist (idempotent — skipped on subsequent starts).</li>
 * </ol>
 *
 * <p>Runs synchronously during CDI startup, before the HTTP server accepts
 * connections, ensuring indexes and the admin user are always in place.
 */
@ApplicationScoped
public class StartupRunner {

    private static final Logger LOG = Logger.getLogger(StartupRunner.class);

    private static final String SEPARATOR =
            "═══════════════════════════════════════════════════════";

    @Inject
    MongoClient mongoClient;

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    AdminConfiguration adminConfiguration;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "rede_studio_dev")
    String databaseName;

    @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
    String profile;

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "rede-studio-api")
    String appName;

    /**
     * Invoked by the CDI container when the application starts.
     *
     * @param event the Quarkus {@link StartupEvent} (injected by CDI)
     */
    void onStart(@Observes StartupEvent event) {
        LOG.info(SEPARATOR);
        LOG.infof("  Application : %s", appName);
        LOG.infof("  Profile     : %s", profile);
        LOG.infof("  Database    : %s", databaseName);
        LOG.info(SEPARATOR);

        createIndexes();
        ensureNetworkStateSchema();
        ensureAdminUser();
    }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    /**
     * Creates unique indexes on {@code email} and {@code username} fields
     * of the users collection.
     *
     * <p>MongoDB's {@code createIndex} is idempotent: if an identical index
     * already exists the call is a no-op, making this safe on every restart.
     */
    private void createIndexes() {
        var collection = mongoClient
                .getDatabase(databaseName)
                .getCollection(CollectionNames.USERS);

        var uniqueIndex = new IndexOptions().unique(true);

        collection.createIndex(Indexes.ascending("email"), uniqueIndex);
        LOG.infof("[Index] users.email    → unique OK");

        collection.createIndex(Indexes.ascending("username"), uniqueIndex);
        LOG.infof("[Index] users.username → unique OK");

        // Índice único, mas PARCIAL: password-only users têm oauth_provider_id
        // presente no documento com valor null (não ausente — @Data/BSON grava
        // o campo mesmo nulo), e um índice único comum trataria todo mundo com
        // esse mesmo null como duplicata do primeiro, quebrando o cadastro do
        // segundo usuário de senha em diante. O filtro por $type restringe a
        // unicidade só a documentos onde o campo é de fato uma string (contas
        // OAuth de verdade).
        var oauthUniqueIndex = new IndexOptions()
                .unique(true)
                .partialFilterExpression(new Document("oauth_provider_id", new Document("$type", "string")));
        collection.createIndex(Indexes.ascending("oauth_provider", "oauth_provider_id"), oauthUniqueIndex);
        LOG.infof("[Index] users.(oauth_provider, oauth_provider_id) → unique partial OK");

        var projectsCollection = mongoClient
                .getDatabase(databaseName)
                .getCollection(CollectionNames.PROJECTS);

        projectsCollection.createIndex(Indexes.ascending("owner_id"));
        LOG.infof("[Index] projects.owner_id → OK");
    }

    /**
     * Array field names required at the top level of every
     * {@code network_states} document — mirrors the frontend's
     * {@code NetworkState} shape (see {@code NetworkStatePayload}).
     */
    private static final List<String> NETWORK_STATE_ARRAY_FIELDS = List.of(
            "sites", "layers", "nodes", "links", "acl_rules", "site_vlans",
            "site_networks", "subnets", "node_vlan_interfaces", "dhcp_scopes",
            "node_qos_profiles", "custom_services", "certificates", "ipsec_sas",
            "ssl_vpn_profiles", "fw_policies", "nat_rules", "active_sessions");

    /** Object field names required at the top level of every {@code network_states} document. */
    private static final List<String> NETWORK_STATE_OBJECT_FIELDS = List.of("counters", "ui", "meta");

    /**
     * Enforces the top-level shape of {@code network_states} documents via a
     * MongoDB {@code $jsonSchema} validator — required presence + type of
     * every field the frontend's {@code NetworkState} always has, without
     * the backend needing to know what's *inside* each array item (that
     * content stays opaque, see {@code NetworkStatePayload}). This is the
     * database-layer half of the "same format for every document" guarantee
     * — the Java-layer half is {@code NetworkStateEntity}'s named fields.
     *
     * <p>Uses {@code createCollection} (with the validator) on first boot,
     * or {@code collMod} on subsequent boots if the collection already
     * exists — both are idempotent, safe to run on every startup.
     */
    private void ensureNetworkStateSchema() {
        var database = mongoClient.getDatabase(databaseName);

        var properties = new Document();
        NETWORK_STATE_ARRAY_FIELDS.forEach(field -> properties.append(field, new Document("bsonType", "array")));
        NETWORK_STATE_OBJECT_FIELDS.forEach(field -> properties.append(field, new Document("bsonType", "object")));

        var required = new ArrayList<String>();
        required.addAll(NETWORK_STATE_ARRAY_FIELDS);
        required.addAll(NETWORK_STATE_OBJECT_FIELDS);

        var validator = new Document("$jsonSchema", new Document()
                .append("bsonType", "object")
                .append("required", required)
                .append("properties", properties));

        boolean exists = database.listCollectionNames()
                .into(new ArrayList<>())
                .contains(CollectionNames.NETWORK_STATES);

        if (!exists) {
            database.createCollection(CollectionNames.NETWORK_STATES,
                    new CreateCollectionOptions().validationOptions(
                            new ValidationOptions()
                                    .validator(validator)
                                    .validationAction(ValidationAction.ERROR)
                                    .validationLevel(ValidationLevel.MODERATE)));
        } else {
            database.runCommand(new Document("collMod", CollectionNames.NETWORK_STATES)
                    .append("validator", validator)
                    .append("validationAction", "error")
                    .append("validationLevel", "moderate"));
        }

        LOG.infof("[Schema] %s → $jsonSchema validator OK", CollectionNames.NETWORK_STATES);
    }

    /**
     * Provisions the default admin user on first run.
     *
     * <p>Checks for the configured admin e-mail before inserting — if the
     * account already exists the method returns immediately without any
     * write operation (idempotent).
     *
     * <p>In the {@code master} profile a warning is logged reminding the
     * operator to rotate the default password after first login.
     */
    private void ensureAdminUser() {
        String email    = adminConfiguration.email();
        String username = adminConfiguration.username();

        if (userRepository.existsByEmail(email)) {
            LOG.infof("[Admin] User '%s' already exists — skipped provisioning.", username);
            return;
        }

        String hashedPassword = passwordHasher.hash(adminConfiguration.password());
        UserEntity admin = UserEntity.create(username, email, hashedPassword, Set.of("USER", "ADMIN"));
        userRepository.persist(admin);

        LOG.infof("[Admin] Default admin provisioned → username: '%s' | email: '%s' | roles: [USER, ADMIN]",
                username, email);

        if ("master".equals(profile)) {
            LOG.warnf("[Security] ADMIN_PASSWORD must be rotated on first login in production!");
        }
    }
}
