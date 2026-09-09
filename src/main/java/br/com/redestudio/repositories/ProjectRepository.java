package br.com.redestudio.repositories;

import br.com.redestudio.entities.ProjectEntity;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ProjectEntity} documents in MongoDB.
 *
 * <p>Every query is scoped by {@code ownerId} at the database level —
 * never fetch by id alone and check ownership afterwards, to avoid IDOR
 * (a project id from another user's session must never resolve).
 */
@ApplicationScoped
public class ProjectRepository implements PanacheMongoRepository<ProjectEntity> {

    /**
     * Lists every project owned by the given user, most recently updated first.
     *
     * @param ownerId the owner's Mongo {@code _id} (JWT {@code uid} claim)
     * @return list of matching projects (may be empty)
     */
    public List<ProjectEntity> listByOwnerId(ObjectId ownerId) {
        return find("{'owner_id': ?1}", Sort.descending("updated_at"), ownerId).list();
    }

    /**
     * Finds a single project by id, scoped to its owner.
     *
     * @param id      the project id (hex string, possibly malformed)
     * @param ownerId the owner's Mongo {@code _id} (JWT {@code uid} claim)
     * @return an {@link Optional} with the project, or empty if the id is
     *         malformed, the project doesn't exist, or it belongs to someone else
     */
    public Optional<ProjectEntity> findByIdAndOwnerId(String id, ObjectId ownerId) {
        if (!ObjectId.isValid(id)) {
            return Optional.empty();
        }
        return find("{'_id': ?1, 'owner_id': ?2}", new ObjectId(id), ownerId).firstResultOptional();
    }

    /**
     * Deletes a project by id, scoped to its owner.
     *
     * @param id      the project id (hex string, possibly malformed)
     * @param ownerId the owner's Mongo {@code _id} (JWT {@code uid} claim)
     * @return the number of documents deleted (0 if the id is malformed,
     *         the project doesn't exist, or it belongs to someone else)
     */
    public long deleteByIdAndOwnerId(String id, ObjectId ownerId) {
        if (!ObjectId.isValid(id)) {
            return 0;
        }
        return delete("{'_id': ?1, 'owner_id': ?2}", new ObjectId(id), ownerId);
    }
}
