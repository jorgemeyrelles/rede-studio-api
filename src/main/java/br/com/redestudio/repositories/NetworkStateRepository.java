package br.com.redestudio.repositories;

import br.com.redestudio.entities.NetworkStateEntity;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for {@link NetworkStateEntity} documents in MongoDB.
 *
 * <p>No custom queries needed yet — access is always by {@code _id}
 * (via {@code ProjectEntity.networkStateId}), covered by the base
 * {@code findById}/{@code persist}/{@code update}/{@code deleteById}
 * from {@link PanacheMongoRepository}.
 */
@ApplicationScoped
public class NetworkStateRepository implements PanacheMongoRepository<NetworkStateEntity> {
}
