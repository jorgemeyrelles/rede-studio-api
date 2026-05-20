package br.com.redestudio.runners;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.components.PasswordHasher;
import br.com.redestudio.configurations.AdminConfiguration;
import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.repositories.UserRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

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
