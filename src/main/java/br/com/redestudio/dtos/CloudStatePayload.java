package br.com.redestudio.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Shape of the frontend's {@code CloudState} (rede-sp-cwb,
 * {@code src/features/cloud/types}) — the architecture draft of a
 * {@code CLOUD} project. Used both as request payload (create/cloud
 * snapshot) and response payload (get project).
 *
 * <p>Same contract as {@link NetworkStatePayload}: only the <b>top-level</b>
 * keys are modeled; the content of each resource/connection is opaque and
 * evolves on the frontend. A MongoDB {@code $jsonSchema} validator on
 * {@code cloud_states} (see {@code StartupRunner}) enforces the same shape
 * at the database layer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudStatePayload", description = "Top-level shape of a cloud project's architecture draft; inner content is opaque")
public class CloudStatePayload {

    @NotNull
    @Schema(description = "Draft format version (frontend-owned)", example = "1")
    private Integer schemaVersion;

    @NotBlank
    @Schema(description = "Cloud provider family of the draft", example = "AWS")
    private String provider;

    /** Account/IAM, regions, VPCs, AZs, subnets and resources — flat list linked by parentId. */
    @NotNull
    private List<Object> resources;

    /** Edges between resources (direction, protocol, port...). */
    @NotNull
    private List<Object> connections;

    /** Region, hours/month, transfer volumes, currency... */
    @NotNull
    private Map<String, Object> assumptions;

    @NotNull
    private Map<String, Object> ui;

    @NotNull
    private Map<String, Object> meta;
}
