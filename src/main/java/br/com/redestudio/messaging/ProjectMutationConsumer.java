package br.com.redestudio.messaging;

import br.com.redestudio.entities.NetworkStateEntity;
import br.com.redestudio.entities.ProjectEntity;
import br.com.redestudio.repositories.NetworkStateRepository;
import br.com.redestudio.repositories.ProjectRepository;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Consumes project mutation events and performs the actual MongoDB writes —
 * the synchronous request path (see {@code ProjectService}) only updates
 * Redis and publishes here, it never touches Mongo directly.
 *
 * <p>Every message carries ids pre-generated (or already known) by the
 * synchronous path, so persistence here is always a deterministic
 * insert/update by a known {@code _id} — never a "create if not exists"
 * guess.
 */
@ApplicationScoped
public class ProjectMutationConsumer {

    private static final Logger LOG = Logger.getLogger(ProjectMutationConsumer.class);

    @Inject
    ProjectRepository projectRepository;

    @Inject
    NetworkStateRepository networkStateRepository;

    @Incoming("project-create-in")
    public void onCreate(JsonObject payload) {
        ProjectCreateMessage message = payload.mapTo(ProjectCreateMessage.class);

        NetworkStateEntity networkState = NetworkStateEntity.fromPayload(message.networkState());
        networkState.setId(new ObjectId(message.networkStateId()));
        networkStateRepository.persist(networkState);

        ProjectEntity project = ProjectEntity.create(
                new ObjectId(message.ownerId()), message.name(), networkState.getId());
        project.setId(new ObjectId(message.projectId()));
        projectRepository.persist(project);
    }

    @Incoming("project-rename-in")
    public void onRename(JsonObject payload) {
        ProjectRenameMessage message = payload.mapTo(ProjectRenameMessage.class);

        ProjectEntity project = projectRepository.findById(new ObjectId(message.projectId()));
        if (project == null) {
            LOG.warnf("project.rename: project %s not found (already deleted?)", message.projectId());
            return;
        }
        project.setName(message.name());
        project.setUpdatedAt(Instant.now());
        projectRepository.update(project);
    }

    @Incoming("project-snapshot-in")
    public void onSnapshot(JsonObject payload) {
        ProjectSnapshotMessage message = payload.mapTo(ProjectSnapshotMessage.class);

        ProjectEntity project = projectRepository.findById(new ObjectId(message.projectId()));
        if (project == null) {
            LOG.warnf("project.snapshot: project %s not found (already deleted?)", message.projectId());
            return;
        }

        NetworkStateEntity networkState = networkStateRepository.findById(project.getNetworkStateId());
        if (networkState == null) {
            // Referência quebrada (nunca deveria acontecer — create sempre cria as
            // duas entidades juntas) — auto-cura recriando a partir do snapshot
            // recebido, que sempre traz conteúdo real, diferente de um GET.
            LOG.warnf("project.snapshot: networkState %s missing for project %s — recreating",
                    project.getNetworkStateId(), project.getId());
            networkState = NetworkStateEntity.fromPayload(message.networkState());
            networkStateRepository.persist(networkState);
            project.setNetworkStateId(networkState.getId());
        } else {
            networkState.applyPayload(message.networkState());
            networkStateRepository.update(networkState);
        }

        project.setUpdatedAt(Instant.now());
        projectRepository.update(project);
    }

    @Incoming("project-delete-in")
    public void onDelete(JsonObject payload) {
        ProjectDeleteMessage message = payload.mapTo(ProjectDeleteMessage.class);

        ProjectEntity project = projectRepository.findById(new ObjectId(message.projectId()));
        if (project == null) {
            return;
        }
        networkStateRepository.deleteById(project.getNetworkStateId());
        projectRepository.deleteById(project.getId());
    }
}
