package br.com.redestudio.entities;

/**
 * Network topology category of a project — chosen once at creation time
 * and never editable afterward (no endpoint accepts changing it). Lays
 * the groundwork for future cloud topology modeling (AWS, not implemented
 * in this sprint): {@code CLOUD}/{@code MULTI_CLOUD}/{@code HYBRID}
 * projects are where that configuration will eventually live.
 *
 * <p>Projects created before this field existed have {@code null} here on
 * the entity; callers treat that as {@link #ON_PREMISE} (see
 * {@code ProjectResponse#from}/{@code ProjectSummaryResponse#from} and
 * {@code ProjectMutationConsumer#onRename}/{@code #onSnapshot}, which also
 * persist that default the next time such a project is touched).
 */
public enum NetworkType {
    ON_PREMISE,
    CLOUD,
    MULTI_CLOUD,
    HYBRID
}
