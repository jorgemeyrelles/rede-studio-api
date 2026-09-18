package br.com.redestudio.repositories;

import br.com.redestudio.entities.EquipmentEntity;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for {@link EquipmentEntity} documents in MongoDB.
 *
 * <p>Custom queries use native Mongo syntax (not Panache's simplified
 * field-based DSL) for anything beyond a single exact-match field — this
 * project already hit a real bug where the simplified syntax silently
 * failed to translate (see {@code UserRepository#findByUsernameContains}),
 * so native syntax is the established, trusted pattern here.
 */
@ApplicationScoped
public class EquipmentRepository implements PanacheMongoRepository<EquipmentEntity> {

    /**
     * Lists every equipment of the given brand, alphabetically by model.
     *
     * @param brand the brand to filter by (exact match)
     * @return list of matching equipment (may be empty)
     */
    public List<EquipmentEntity> listByBrand(String brand) {
        return find("{'brand': ?1}", Sort.ascending("model"), brand).list();
    }

    /**
     * Finds equipment whose model contains the given substring, case-insensitively.
     *
     * @param partial the substring to search for (treated as a literal, not a regex)
     * @return list of matching equipment (may be empty)
     */
    public List<EquipmentEntity> findByModelContains(String partial) {
        String safePattern = escapeRegex(partial);
        return find("{'model': {$regex: ?1, $options: 'i'}}", safePattern).list();
    }

    /**
     * Escapes regex metacharacters so a user-supplied substring is matched
     * literally. Backslash-escaping is portable between Java's regex engine
     * and MongoDB's (PCRE-based) — unlike {@link java.util.regex.Pattern#quote},
     * whose {@code \Q...\E} delimiters are Java-specific and would be sent to
     * MongoDB as literal characters instead of being interpreted as quoting.
     */
    private static String escapeRegex(String input) {
        return input.replaceAll("([.^$|()\\[\\]{}*+?\\\\])", "\\\\$1");
    }
}
