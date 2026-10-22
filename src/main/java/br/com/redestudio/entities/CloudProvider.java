package br.com.redestudio.entities;

/**
 * Cloud provider family of a price entry ({@code rede_cloud.cloud_prices})
 * or of a cloud architecture draft. Only {@link #AWS} has prices loaded
 * today; the others exist so the model doesn't change when they arrive.
 */
public enum CloudProvider {
    AWS,
    AZURE,
    GCP,
    OCI
}
