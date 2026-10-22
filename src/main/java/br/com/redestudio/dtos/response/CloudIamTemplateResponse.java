package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.CloudIamTemplateEntity;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.entities.IamTemplateKind;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Um modelo do catálogo IAM (grupo, role ou política gerenciada). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudIamTemplateResponse", description = "IAM catalog template")
public class CloudIamTemplateResponse {

    private CloudProvider provider;
    private IamTemplateKind kind;
    private String key;
    private Map<String, String> name;
    private Map<String, String> description;
    private String iamName;
    private List<String> policies;
    private String trustedService;
    private String policyArn;
    private Integer order;
    private Instant createdAt;
    private Instant updatedAt;

    public static CloudIamTemplateResponse from(CloudIamTemplateEntity e) {
        return new CloudIamTemplateResponse(
                e.getProvider(), e.getKind(), e.getKey(), e.getName(), e.getDescription(), e.getIamName(),
                e.getPolicies(), e.getTrustedService(), e.getPolicyArn(), e.getOrder(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
