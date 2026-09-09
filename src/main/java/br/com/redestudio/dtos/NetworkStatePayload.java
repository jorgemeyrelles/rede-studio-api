package br.com.redestudio.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Shape of the frontend's {@code NetworkState} (rede-sp-cwb,
 * {@code src/features/network/types/entities.ts}), used both as the
 * request payload (create/snapshot) and the response payload (get project).
 *
 * <p>Only the <b>top-level</b> shape is modeled — every field here is a
 * required top-level key of {@code NetworkState}. The content of each list
 * item (a {@code Site}, {@code NodeItem}, {@code AclRule}, etc.) is
 * deliberately left opaque (plain {@code Object}/{@code Map}): that inner
 * schema evolves independently on the frontend and the backend has no
 * business modeling it field-by-field in Java. Enforcing that every one of
 * these top-level keys is always present, with the right type, is also
 * done at the database layer via a MongoDB {@code $jsonSchema} validator on
 * the {@code network_states} collection (see {@code StartupRunner}) — this
 * class is the same contract enforced at the API boundary.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "NetworkStatePayload", description = "Top-level shape of a project's network state; each field's inner content is opaque")
public class NetworkStatePayload {

    @NotNull
    private List<Object> sites;

    @NotNull
    private List<Object> layers;

    @NotNull
    private List<Object> nodes;

    @NotNull
    private List<Object> links;

    @NotNull
    private List<Object> aclRules;

    @NotNull
    private List<Object> siteVlans;

    @NotNull
    private List<Object> siteNetworks;

    @NotNull
    private List<Object> subnets;

    @NotNull
    private List<Object> nodeVlanInterfaces;

    @NotNull
    private List<Object> dhcpScopes;

    @NotNull
    private List<Object> nodeQosProfiles;

    @NotNull
    private List<Object> customServices;

    @NotNull
    private List<Object> certificates;

    @NotNull
    private List<Object> ipsecSas;

    @NotNull
    private List<Object> sslVpnProfiles;

    @NotNull
    private List<Object> fwPolicies;

    @NotNull
    private List<Object> natRules;

    @NotNull
    private List<Object> activeSessions;

    @NotNull
    private Map<String, Object> counters;

    @NotNull
    private Map<String, Object> ui;

    @NotNull
    private Map<String, Object> meta;
}
