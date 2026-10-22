package br.com.redestudio.repositories;

import br.com.redestudio.entities.CloudStateEntity;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for {@link CloudStateEntity} documents in MongoDB.
 *
 * <p>Access is always by {@code _id} (via {@code ProjectEntity.cloudStateId}),
 * same as {@link NetworkStateRepository}.
 */
@ApplicationScoped
public class CloudStateRepository implements PanacheMongoRepository<CloudStateEntity> {
}
