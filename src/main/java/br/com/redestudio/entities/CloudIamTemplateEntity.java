package br.com.redestudio.entities;

import br.com.redestudio.dtos.request.CloudIamTemplateItemRequest;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Um modelo do catálogo IAM do Studio Cloud, no banco cloud
 * ({@code app.cloud-pricing.database}, coleção {@code cloud_iam_templates}).
 *
 * <p>Os provedores não oferecem "grupos prontos" — oferecem políticas
 * gerenciadas. O catálogo guarda por isso <em>modelos</em>: um GROUP/ROLE
 * referencia, por {@code key}, as POLICY que traz por padrão; cada POLICY
 * carrega o identificador real no provedor ({@code policyArn}), que o
 * gerador de Terraform usa.
 *
 * <p>Chave natural {@code (provider, key)}; recargas fazem upsert,
 * preservando {@code createdAt} e avançando {@code updatedAt} — mesmo
 * contrato de {@link CloudPriceEntity}.
 */
@Data
@NoArgsConstructor
public class CloudIamTemplateEntity {

    private String id;

    /** Família do provedor — o agrupamento do catálogo. */
    private CloudProvider provider;

    private IamTemplateKind kind;

    /** Ex.: {@code aws.group.network-admins}, {@code aws.policy.network-administrator}. */
    private String key;

    /** Rótulo por idioma ({@code pt}/{@code en}/{@code es}). */
    private Map<String, String> name;

    private Map<String, String> description;

    /** GROUP/ROLE: nome sugerido no provedor (ex.: {@code NetworkAdmins}). */
    private String iamName;

    /** GROUP/ROLE: {@code key}s das POLICY trazidas por padrão. */
    private List<String> policies;

    /** ROLE: serviço que assume a role (ex.: {@code ec2.amazonaws.com}). */
    private String trustedService;

    /** POLICY: identificador no provedor (ARN na AWS). */
    private String policyArn;

    /** Ordem de exibição dentro do mesmo {@code kind}. */
    private Integer order;

    private Instant createdAt;

    private Instant updatedAt;

    /** Builds an entity from the API/seed item shape (timestamps are set by the upsert). */
    public static CloudIamTemplateEntity fromRequest(CloudIamTemplateItemRequest item) {
        CloudIamTemplateEntity entity = new CloudIamTemplateEntity();
        entity.setProvider(item.getProvider());
        entity.setKind(item.getKind());
        entity.setKey(item.getKey());
        entity.setName(item.getName());
        entity.setDescription(item.getDescription());
        entity.setIamName(item.getIamName());
        entity.setPolicies(item.getPolicies());
        entity.setTrustedService(item.getTrustedService());
        entity.setPolicyArn(item.getPolicyArn());
        entity.setOrder(item.getOrder());
        return entity;
    }
}
