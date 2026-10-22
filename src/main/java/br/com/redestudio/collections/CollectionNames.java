package br.com.redestudio.collections;

/**
 * Central registry of MongoDB collection names.
 * Use these constants everywhere a collection name is needed
 * to avoid typos and make renames a single-point change.
 */
public final class CollectionNames {

    private CollectionNames() {
    }

    public static final String USERS = "users";

    public static final String PROJECTS = "projects";

    public static final String NETWORK_STATES = "network_states";

    public static final String EQUIPMENTS = "equipments";

    /** Architecture drafts of {@code CLOUD} projects (counterpart of {@link #NETWORK_STATES}). */
    public static final String CLOUD_STATES = "cloud_states";

    /**
     * Cloud price catalog — lives in the separate cloud database
     * ({@code app.cloud-pricing.database}), not in the main one.
     */
    public static final String CLOUD_PRICES = "cloud_prices";

    /**
     * Catálogo IAM (modelos de grupo/role e políticas gerenciadas) por
     * família de provedor — também no banco cloud, como os preços.
     */
    public static final String CLOUD_IAM_TEMPLATES = "cloud_iam_templates";
}
