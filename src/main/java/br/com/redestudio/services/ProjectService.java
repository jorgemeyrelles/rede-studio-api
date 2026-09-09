package br.com.redestudio.services;

import br.com.redestudio.dtos.NetworkStatePayload;
import br.com.redestudio.dtos.request.CreateProjectRequest;
import br.com.redestudio.dtos.response.ProjectResponse;
import br.com.redestudio.dtos.response.ProjectSummaryResponse;
import br.com.redestudio.entities.NetworkStateEntity;
import br.com.redestudio.entities.ProjectEntity;
import br.com.redestudio.messaging.ProjectCreateMessage;
import br.com.redestudio.messaging.ProjectDeleteMessage;
import br.com.redestudio.messaging.ProjectMutationProducer;
import br.com.redestudio.messaging.ProjectRenameMessage;
import br.com.redestudio.messaging.ProjectSnapshotMessage;
import br.com.redestudio.repositories.NetworkStateRepository;
import br.com.redestudio.repositories.ProjectRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for project lifecycle operations.
 *
 * <p>Follows the LB → Redis → fila → BD pattern (see {@code CLAUDE.md}):
 * every write updates {@link ProjectCacheService} synchronously and
 * publishes to a dedicated queue for {@code ProjectMutationConsumer} to
 * apply to MongoDB later; every read checks the cache first, falling back
 * to Mongo (via {@link ProjectRepository}/{@link NetworkStateRepository})
 * only on a miss. "Not found" and "found but belongs to someone else" are
 * deliberately indistinguishable to the caller (both throw
 * {@link NotFoundException}) — the same non-enumeration pattern already
 * used by {@link AuthService#login}.
 */
@ApplicationScoped
public class ProjectService {

    private static final Logger LOG = Logger.getLogger(ProjectService.class);

    @Inject
    ProjectRepository projectRepository;

    @Inject
    NetworkStateRepository networkStateRepository;

    @Inject
    ProjectCacheService cacheService;

    @Inject
    ProjectMutationProducer producer;

    /**
     * Lists every project owned by the given user — Redis first, Mongo on a miss.
     *
     * @param ownerId the owner's Mongo {@code _id} (JWT {@code uid} claim)
     * @return list of project summaries (may be empty)
     */
    public List<ProjectSummaryResponse> listMine(ObjectId ownerId) {
        String ownerKey = ownerId.toHexString();
        return cacheService.getOwnerList(ownerKey).orElseGet(() -> {
            List<ProjectSummaryResponse> items = projectRepository.listByOwnerId(ownerId).stream()
                    .map(ProjectSummaryResponse::from)
                    .toList();
            cacheService.putOwnerList(ownerKey, items);
            return items;
        });
    }

    /**
     * Fetches a single project owned by the given user — Redis first, Mongo
     * on a miss. Ownership is re-checked even on a cache hit, since Redis
     * has no access control of its own.
     *
     * @param id      the project id
     * @param ownerId the owner's Mongo {@code _id} (JWT {@code uid} claim)
     * @return the full project
     * @throws NotFoundException if the project doesn't exist or isn't owned by this user
     */
    public ProjectResponse getOwned(String id, ObjectId ownerId) {
        String ownerKey = ownerId.toHexString();

        Optional<ProjectResponse> cached = cacheService.getProject(id)
                .filter(p -> ownerKey.equals(p.getOwnerId()));
        if (cached.isPresent()) {
            return cached.get();
        }

        ProjectResponse response = loadFromMongo(id, ownerId);
        cacheService.putProject(response);
        return response;
    }

    /**
     * Creates a new project. Pre-generates both the project and network
     * state ids so the Redis entry written here and the MongoDB documents
     * eventually written by the consumer always agree on the same ids.
     *
     * @param ownerId the owner's Mongo {@code _id} (JWT {@code uid} claim)
     * @param request name and initial network state snapshot
     * @return the created project
     */
    public ProjectResponse create(ObjectId ownerId, CreateProjectRequest request) {
        ObjectId projectId = new ObjectId();
        ObjectId networkStateId = new ObjectId();
        Instant now = Instant.now();

        ProjectResponse response = new ProjectResponse(
                projectId.toHexString(),
                request.getName(),
                ownerId.toHexString(),
                now,
                now,
                request.getNetworkState());

        cacheService.putProject(response);
        prependToOwnerList(ownerId, response.toSummary());

        producer.publishCreate(new ProjectCreateMessage(
                projectId.toHexString(),
                networkStateId.toHexString(),
                ownerId.toHexString(),
                request.getName(),
                request.getNetworkState()));

        return response;
    }

    /**
     * Renames a project owned by the given user.
     *
     * @param id      the project id
     * @param ownerId the owner's Mongo {@code _id} (JWT {@code uid} claim)
     * @param name    the new name
     * @return the updated project summary
     * @throws NotFoundException if the project doesn't exist or isn't owned by this user
     */
    public ProjectSummaryResponse rename(String id, ObjectId ownerId, String name) {
        ProjectResponse current = getOwned(id, ownerId);
        ProjectResponse updated = new ProjectResponse(
                current.getId(), name, current.getOwnerId(),
                current.getCreatedAt(), Instant.now(), current.getNetworkState());

        cacheService.putProject(updated);
        replaceInOwnerList(ownerId, updated.toSummary());

        producer.publishRename(new ProjectRenameMessage(id, ownerId.toHexString(), name));
        return updated.toSummary();
    }

    /**
     * Saves a full network state snapshot for a project owned by the given user.
     *
     * @param id           the project id
     * @param ownerId      the owner's Mongo {@code _id} (JWT {@code uid} claim)
     * @param networkState the new network state snapshot
     * @return the updated project summary
     * @throws NotFoundException if the project doesn't exist or isn't owned by this user
     */
    public ProjectSummaryResponse saveSnapshot(String id, ObjectId ownerId, NetworkStatePayload networkState) {
        ProjectResponse current = getOwned(id, ownerId);
        ProjectResponse updated = new ProjectResponse(
                current.getId(), current.getName(), current.getOwnerId(),
                current.getCreatedAt(), Instant.now(), networkState);

        cacheService.putProject(updated);
        replaceInOwnerList(ownerId, updated.toSummary());

        producer.publishSnapshot(new ProjectSnapshotMessage(id, ownerId.toHexString(), networkState));
        return updated.toSummary();
    }

    /**
     * Deletes a project owned by the given user.
     *
     * @param id      the project id
     * @param ownerId the owner's Mongo {@code _id} (JWT {@code uid} claim)
     * @throws NotFoundException if the project doesn't exist or isn't owned by this user
     */
    public void delete(String id, ObjectId ownerId) {
        getOwned(id, ownerId); // valida existência/posse antes de qualquer efeito

        cacheService.evictProject(id);
        List<ProjectSummaryResponse> updatedList = new ArrayList<>(listMine(ownerId));
        updatedList.removeIf(p -> p.getId().equals(id));
        cacheService.putOwnerList(ownerId.toHexString(), updatedList);

        producer.publishDelete(new ProjectDeleteMessage(id, ownerId.toHexString()));
    }

    private void prependToOwnerList(ObjectId ownerId, ProjectSummaryResponse summary) {
        List<ProjectSummaryResponse> updatedList = new ArrayList<>(listMine(ownerId));
        updatedList.add(0, summary);
        cacheService.putOwnerList(ownerId.toHexString(), updatedList);
    }

    private void replaceInOwnerList(ObjectId ownerId, ProjectSummaryResponse summary) {
        List<ProjectSummaryResponse> updatedList = new ArrayList<>(listMine(ownerId));
        updatedList.removeIf(p -> p.getId().equals(summary.getId()));
        updatedList.add(0, summary);
        cacheService.putOwnerList(ownerId.toHexString(), updatedList);
    }

    /**
     * Loads a project directly from MongoDB (cache miss path), joining
     * {@code projects} with its {@code network_states} document.
     *
     * @throws NotFoundException if the project doesn't exist, isn't owned by
     *                            this user, or (integrity error — should
     *                            never happen, create/delete always keep
     *                            both entities together) its network state
     *                            document is missing
     */
    private ProjectResponse loadFromMongo(String id, ObjectId ownerId) {
        ProjectEntity project = projectRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));

        NetworkStateEntity networkState = networkStateRepository.findById(project.getNetworkStateId());
        if (networkState == null) {
            LOG.errorf("Integrity error: project %s references missing networkState %s",
                    id, project.getNetworkStateId());
            throw new NotFoundException("Project data is corrupted: " + id);
        }

        return ProjectResponse.from(project, networkState);
    }
}
