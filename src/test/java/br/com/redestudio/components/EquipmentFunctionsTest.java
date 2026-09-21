package br.com.redestudio.components;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain unit test (no Quarkus) — covers every distinct legacy {@code function}
 * string present in the seeded equipment catalog.
 */
class EquipmentFunctionsTest {

    @Test
    void fromLegacy_singleFunctions() {
        assertEquals(List.of("roteador"), EquipmentFunctions.fromLegacy("roteador"));
        assertEquals(List.of("firewall"), EquipmentFunctions.fromLegacy("firewall"));
        assertEquals(List.of("switch"), EquipmentFunctions.fromLegacy("switch"));
        assertEquals(List.of("access point"), EquipmentFunctions.fromLegacy("access point"));
    }

    @Test
    void fromLegacy_combinedFunctions_keepOriginalOrder() {
        assertEquals(List.of("roteador", "firewall", "gateway"), EquipmentFunctions.fromLegacy("roteador/firewall/gateway"));
        assertEquals(List.of("roteador", "firewall", "vpn"), EquipmentFunctions.fromLegacy("roteador/firewall VPN"));
        assertEquals(List.of("firewall", "roteador"), EquipmentFunctions.fromLegacy("firewall/roteador"));
        assertEquals(List.of("roteador", "access point"), EquipmentFunctions.fromLegacy("roteador Wi-Fi (SMB)"));
    }

    @Test
    void fromLegacy_unrecognizedTextIsKeptWhole_blankIsEmpty() {
        assertEquals(List.of("appliance misterioso"), EquipmentFunctions.fromLegacy(" Appliance Misterioso "));
        assertEquals(List.of(), EquipmentFunctions.fromLegacy(null));
        assertEquals(List.of(), EquipmentFunctions.fromLegacy("  "));
    }

    @Test
    void normalize_trimsLowercasesDeduplicatesAndDropsBlanks() {
        assertEquals(List.of("roteador", "gateway"),
                EquipmentFunctions.normalize(Arrays.asList(" Roteador ", "roteador", "", null, "GATEWAY")));
        assertEquals(List.of(), EquipmentFunctions.normalize(null));
    }
}
