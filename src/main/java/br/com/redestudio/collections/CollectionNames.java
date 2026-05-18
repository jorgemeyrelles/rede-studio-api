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
}
