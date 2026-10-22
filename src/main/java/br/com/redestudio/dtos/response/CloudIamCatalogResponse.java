package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.CloudProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/** Catálogo IAM de uma família de provedor, já separado por tipo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudIamCatalogResponse", description = "IAM catalog of one provider family, grouped by kind")
public class CloudIamCatalogResponse {

    private CloudProvider provider;
    private List<CloudIamTemplateResponse> groups;
    private List<CloudIamTemplateResponse> roles;
    private List<CloudIamTemplateResponse> policies;
}
