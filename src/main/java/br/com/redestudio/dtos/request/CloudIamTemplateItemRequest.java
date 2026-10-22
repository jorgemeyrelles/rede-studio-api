package br.com.redestudio.dtos.request;

import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.entities.IamTemplateKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** Um modelo IAM num {@code bulk-upsert} — formato de {@code seed/cloud-catalog/*-iam.json}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudIamTemplateItemRequest", description = "One IAM catalog template (group, role or managed policy)")
public class CloudIamTemplateItemRequest {

    @NotNull(message = "provider is required")
    private CloudProvider provider;

    @NotNull(message = "kind is required")
    private IamTemplateKind kind;

    @NotBlank(message = "key is required")
    @Pattern(regexp = "[a-z0-9.-]{3,96}", message = "key must be lowercase dotted, e.g. aws.group.network-admins")
    @Schema(example = "aws.group.network-admins", required = true)
    private String key;

    @NotEmpty(message = "name is required")
    @Schema(description = "Label per language (pt/en/es)", required = true)
    private Map<String, String> name;

    private Map<String, String> description;

    @Schema(description = "GROUP/ROLE: suggested name in the provider", example = "NetworkAdmins")
    private String iamName;

    @Schema(description = "GROUP/ROLE: keys of the POLICY templates included by default")
    private List<String> policies;

    @Schema(description = "ROLE: service principal that assumes the role", example = "ec2.amazonaws.com")
    private String trustedService;

    @Schema(description = "POLICY: identifier in the provider (ARN on AWS)",
            example = "arn:aws:iam::aws:policy/job-function/NetworkAdministrator")
    private String policyArn;

    private Integer order;
}
