package br.com.redestudio.dtos.response;

import br.com.redestudio.dtos.NetworkStatePayload;
import br.com.redestudio.entities.NetworkStateEntity;
import br.com.redestudio.entities.ProjectEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Full project representation, including the network state snapshot —
 * used when fetching a single project.
 *
 * <p>Assembled from two collections ({@code projects} +
 * {@code network_states}, joined by {@code ProjectEntity.networkStateId})
 * via {@link #from(ProjectEntity, NetworkStateEntity)}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ProjectResponse", description = "Full project data, including the network state snapshot")
public class ProjectResponse {

    @Schema(description = "MongoDB document id (hex string)", example = "663f1a2b3c4d5e6f7a8b9c0d")
    private String id;

    @Schema(description = "Project display name", example = "Rede Matriz SP")
    private String name;

    @Schema(description = "Owner's Mongo _id (hex string)", example = "663f0a1b2c3d4e5f6a7b8c9d")
    private String ownerId;

    @Schema(description = "Project creation timestamp (UTC)")
    private Instant createdAt;

    @Schema(description = "Last update timestamp (UTC)")
    private Instant updatedAt;

    @Schema(description = "Full network state snapshot")
    private NetworkStatePayload networkState;

    /**
     * Maps a {@link ProjectEntity} + its associated {@link NetworkStateEntity}
     * to a {@link ProjectResponse}.
     *
     * @param project      source project entity (must not be null)
     * @param networkState the project's associated network state entity (must not be null)
     * @return a new {@link ProjectResponse} instance
     */
    public static ProjectResponse from(ProjectEntity project, NetworkStateEntity networkState) {
        return new ProjectResponse(
                project.getId().toHexString(),
                project.getName(),
                project.getOwnerId().toHexString(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                networkState.toPayload());
    }

    public ProjectSummaryResponse toSummary() {
        return new ProjectSummaryResponse(id, name, ownerId, createdAt, updatedAt);
    }
}
