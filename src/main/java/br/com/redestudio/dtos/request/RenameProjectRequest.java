package br.com.redestudio.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for renaming an existing project.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RenameProjectRequest", description = "Payload for renaming a project")
public class RenameProjectRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "New project display name", example = "Rede Matriz SP", required = true)
    private String name;
}
