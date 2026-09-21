package br.com.redestudio.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Helpers for an equipment's {@code function} list (e.g.
 * {@code ["roteador", "firewall", "gateway"]}).
 *
 * <p>The frontend offers an equipment on a node only when the node type's
 * function term (router → "roteador", firewall → "firewall", ...) is in
 * this list, so the terms are stored trimmed and lowercase.
 */
public final class EquipmentFunctions {

    /** Canonical term → keywords that identify it inside a legacy free-text function. */
    private static final Map<String, List<String>> LEGACY_KEYWORDS = new LinkedHashMap<>();

    static {
        LEGACY_KEYWORDS.put("roteador", List.of("roteador", "router"));
        LEGACY_KEYWORDS.put("firewall", List.of("firewall"));
        LEGACY_KEYWORDS.put("gateway", List.of("gateway"));
        LEGACY_KEYWORDS.put("switch", List.of("switch"));
        LEGACY_KEYWORDS.put("access point", List.of("access point", "wi-fi"));
        LEGACY_KEYWORDS.put("vpn", List.of("vpn"));
    }

    private EquipmentFunctions() {
    }

    /** Trims, lowercases, drops blanks and de-duplicates, keeping the given order. */
    public static List<String> normalize(List<String> functions) {
        Set<String> result = new LinkedHashSet<>();
        if (functions != null) {
            for (String raw : functions) {
                String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) result.add(value);
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * Converts the old single-string {@code function} ("roteador/firewall/gateway",
     * "roteador Wi-Fi (SMB)") into the list form, keeping the order in which
     * the terms appeared. Text with no recognized term is kept whole as a
     * single element so no information is lost.
     */
    public static List<String> fromLegacy(String legacy) {
        String text = legacy == null ? "" : legacy.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return new ArrayList<>();

        TreeMap<Integer, String> byPosition = new TreeMap<>();
        LEGACY_KEYWORDS.forEach((term, keywords) -> keywords.stream()
                .mapToInt(text::indexOf)
                .filter(index -> index >= 0)
                .min()
                .ifPresent(index -> byPosition.putIfAbsent(index, term)));

        return byPosition.isEmpty() ? new ArrayList<>(List.of(text)) : new ArrayList<>(byPosition.values());
    }
}
