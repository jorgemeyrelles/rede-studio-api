package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.ProjectEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Project metadata without the network state snapshot — used for listing.
 *
 * <p>Created via {@link #from(ProjectEntity)} to keep mapping logic
 * centralised.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ProjectSummaryResponse", description = "Project metadata, without the network state snapshot")
public class ProjectSummaryResponse {

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

    /**
     * Maps a {@link ProjectEntity} to a {@link ProjectSummaryResponse}.
     *
     * @param entity source entity (must not be null)
     * @return a new {@link ProjectSummaryResponse} instance
     */
    public static ProjectSummaryResponse from(ProjectEntity entity) {
        return new ProjectSummaryResponse(
                entity.getId().toHexString(),
                entity.getName(),
                entity.getOwnerId().toHexString(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
