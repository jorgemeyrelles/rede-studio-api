package br.com.redestudio.entities;

import br.com.redestudio.collections.CollectionNames;
import br.com.redestudio.dtos.NetworkStatePayload;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document holding one project's network topology snapshot.
 * Collection: {@value CollectionNames#NETWORK_STATES}
 *
 * <p>1:1 with a {@link ProjectEntity} (via {@code ProjectEntity.networkStateId})
 * — always created and deleted together with its owning project, never
 * shared or orphaned. Top-level fields mirror the frontend's
 * {@code NetworkState} shape (see {@link NetworkStatePayload}); the content
 * of each list/map is opaque on purpose. A MongoDB {@code $jsonSchema}
 * validator on this collection (set up in {@code StartupRunner}) enforces
 * the same top-level shape at the database layer, independent of the Java
 * write path.
 *
 * <p>Fields are {@link Document} (not {@code Object}/{@code Map<String,Object>})
 * on purpose: the MongoDB driver's automatic POJO codec needs a concrete,
 * registered codec for every field's <em>declared</em> type, and there's no
 * such codec for a bare {@code Object} — {@link Document} has one built in,
 * and (unlike POJOs) encodes/decodes arbitrary nested content dynamically at
 * runtime, which is exactly the "opaque" behavior this entity needs.
 */
@Data
@NoArgsConstructor
@MongoEntity(collection = CollectionNames.NETWORK_STATES)
public class NetworkStateEntity {

    @BsonId
    private ObjectId id;

    @BsonProperty("sites")
    private List<Document> sites;

    @BsonProperty("layers")
    private List<Document> layers;

    @BsonProperty("nodes")
    private List<Document> nodes;

    @BsonProperty("links")
    private List<Document> links;

    @BsonProperty("acl_rules")
    private List<Document> aclRules;

    @BsonProperty("site_vlans")
    private List<Document> siteVlans;

    @BsonProperty("site_networks")
    private List<Document> siteNetworks;

    @BsonProperty("subnets")
    private List<Document> subnets;

    @BsonProperty("node_vlan_interfaces")
    private List<Document> nodeVlanInterfaces;

    @BsonProperty("dhcp_scopes")
    private List<Document> dhcpScopes;

    @BsonProperty("node_qos_profiles")
    private List<Document> nodeQosProfiles;

    @BsonProperty("custom_services")
    private List<Document> customServices;

    @BsonProperty("certificates")
    private List<Document> certificates;

    @BsonProperty("ipsec_sas")
    private List<Document> ipsecSas;

    @BsonProperty("ssl_vpn_profiles")
    private List<Document> sslVpnProfiles;

    @BsonProperty("fw_policies")
    private List<Document> fwPolicies;

    @BsonProperty("nat_rules")
    private List<Document> natRules;

    @BsonProperty("active_sessions")
    private List<Document> activeSessions;

    @BsonProperty("counters")
    private Document counters;

    @BsonProperty("ui")
    private Document ui;

    @BsonProperty("meta")
    private Document meta;

    /** Builds a new entity from the API payload — used on create and on snapshot self-heal. */
    public static NetworkStateEntity fromPayload(NetworkStatePayload payload) {
        NetworkStateEntity entity = new NetworkStateEntity();
        entity.applyPayload(payload);
        return entity;
    }

    /** Applies a new payload onto this existing entity (in place) — used on snapshot save. */
    public void applyPayload(NetworkStatePayload payload) {
        setSites(toDocumentList(payload.getSites()));
        setLayers(toDocumentList(payload.getLayers()));
        setNodes(toDocumentList(payload.getNodes()));
        setLinks(toDocumentList(payload.getLinks()));
        setAclRules(toDocumentList(payload.getAclRules()));
        setSiteVlans(toDocumentList(payload.getSiteVlans()));
        setSiteNetworks(toDocumentList(payload.getSiteNetworks()));
        setSubnets(toDocumentList(payload.getSubnets()));
        setNodeVlanInterfaces(toDocumentList(payload.getNodeVlanInterfaces()));
        setDhcpScopes(toDocumentList(payload.getDhcpScopes()));
        setNodeQosProfiles(toDocumentList(payload.getNodeQosProfiles()));
        setCustomServices(toDocumentList(payload.getCustomServices()));
        setCertificates(toDocumentList(payload.getCertificates()));
        setIpsecSas(toDocumentList(payload.getIpsecSas()));
        setSslVpnProfiles(toDocumentList(payload.getSslVpnProfiles()));
        setFwPolicies(toDocumentList(payload.getFwPolicies()));
        setNatRules(toDocumentList(payload.getNatRules()));
        setActiveSessions(toDocumentList(payload.getActiveSessions()));
        setCounters(new Document(payload.getCounters()));
        setUi(new Document(payload.getUi()));
        setMeta(new Document(payload.getMeta()));
    }

    /** Maps this entity back to the API payload shape (get project response). */
    public NetworkStatePayload toPayload() {
        return new NetworkStatePayload(
                toObjectList(sites), toObjectList(layers), toObjectList(nodes), toObjectList(links),
                toObjectList(aclRules), toObjectList(siteVlans), toObjectList(siteNetworks), toObjectList(subnets),
                toObjectList(nodeVlanInterfaces), toObjectList(dhcpScopes), toObjectList(nodeQosProfiles),
                toObjectList(customServices), toObjectList(certificates), toObjectList(ipsecSas),
                toObjectList(sslVpnProfiles), toObjectList(fwPolicies), toObjectList(natRules),
                toObjectList(activeSessions), counters, ui, meta);
    }

    /**
     * Wraps each opaque list item (a {@code LinkedHashMap} as produced by
     * Jackson for an {@code Object}-typed field) into a {@link Document} —
     * only the top level needs this; {@link Document}'s own codec encodes
     * whatever plain {@code Map}/{@code List}/scalar values it holds
     * recursively, so nested content never needs its own conversion.
     */
    @SuppressWarnings("unchecked")
    private static List<Document> toDocumentList(List<Object> items) {
        List<Document> result = new ArrayList<>(items.size());
        for (Object item : items) {
            result.add(item instanceof Map ? new Document((Map<String, Object>) item) : new Document());
        }
        return result;
    }

    private static List<Object> toObjectList(List<Document> items) {
        return new ArrayList<>(items);
    }
}
