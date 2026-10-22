package br.com.redestudio.messaging;

import br.com.redestudio.entities.CloudStateEntity;
import br.com.redestudio.entities.NetworkStateEntity;
import br.com.redestudio.entities.NetworkType;
import br.com.redestudio.entities.ProjectEntity;
import br.com.redestudio.repositories.CloudStateRepository;
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

    @Inject
    CloudStateRepository cloudStateRepository;

    @Incoming("project-create-in")
    public void onCreate(JsonObject payload) {
        ProjectCreateMessage message = payload.mapTo(ProjectCreateMessage.class);

        // Só um dos dois estados vem preenchido (decidido pelo networkType no
        // ProjectService) — persiste o que veio, referencia só ele.
        ObjectId networkStateId = null;
        if (message.networkState() != null) {
            NetworkStateEntity networkState = NetworkStateEntity.fromPayload(message.networkState());
            networkState.setId(new ObjectId(message.networkStateId()));
            networkStateRepository.persist(networkState);
            networkStateId = networkState.getId();
        }

        ObjectId cloudStateId = null;
        if (message.cloudState() != null) {
            CloudStateEntity cloudState = CloudStateEntity.fromPayload(message.cloudState());
            cloudState.setId(new ObjectId(message.cloudStateId()));
            cloudStateRepository.persist(cloudState);
            cloudStateId = cloudState.getId();
        }

        ProjectEntity project = ProjectEntity.create(
                new ObjectId(message.ownerId()), message.name(), message.networkType(), networkStateId, cloudStateId);
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
        backfillNetworkTypeIfMissing(project);
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

        backfillNetworkTypeIfMissing(project);
        project.setUpdatedAt(Instant.now());
        projectRepository.update(project);
    }

    @Incoming("project-cloud-snapshot-in")
    public void onCloudSnapshot(JsonObject payload) {
        ProjectCloudSnapshotMessage message = payload.mapTo(ProjectCloudSnapshotMessage.class);

        ProjectEntity project = projectRepository.findById(new ObjectId(message.projectId()));
        if (project == null) {
            LOG.warnf("project.cloud-snapshot: project %s not found (already deleted?)", message.projectId());
            return;
        }

        CloudStateEntity cloudState = project.getCloudStateId() != null
                ? cloudStateRepository.findById(project.getCloudStateId())
                : null;
        if (cloudState == null) {
            // Mesma auto-cura do onSnapshot: o snapshot sempre traz o rascunho completo.
            LOG.warnf("project.cloud-snapshot: cloudState %s missing for project %s — recreating",
                    project.getCloudStateId(), project.getId());
            cloudState = CloudStateEntity.fromPayload(message.cloudState());
            cloudStateRepository.persist(cloudState);
            project.setCloudStateId(cloudState.getId());
        } else {
            cloudState.applyPayload(message.cloudState());
            cloudStateRepository.update(cloudState);
        }

        project.setUpdatedAt(Instant.now());
        projectRepository.update(project);
    }

    /**
     * Sprint tiers — projeto anterior a esta sprint não tem {@code networkType}
     * no Mongo; em vez de uma migração em lote, qualquer `rename`/`snapshot`
     * que toque o documento já persiste o default {@link NetworkType#ON_PREMISE}
     * dali em diante (mesma regra de leitura usada em {@code ProjectResponse}/
     * {@code ProjectSummaryResponse}).
     */
    private void backfillNetworkTypeIfMissing(ProjectEntity project) {
        if (project.getNetworkType() == null) {
            project.setNetworkType(NetworkType.ON_PREMISE);
        }
    }

    @Incoming("project-delete-in")
    public void onDelete(JsonObject payload) {
        ProjectDeleteMessage message = payload.mapTo(ProjectDeleteMessage.class);

        ProjectEntity project = projectRepository.findById(new ObjectId(message.projectId()));
        if (project == null) {
            return;
        }
        if (project.getNetworkStateId() != null) {
            networkStateRepository.deleteById(project.getNetworkStateId());
        }
        if (project.getCloudStateId() != null) {
            cloudStateRepository.deleteById(project.getCloudStateId());
        }
        projectRepository.deleteById(project.getId());
    }
}
