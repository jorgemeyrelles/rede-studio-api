package br.com.redestudio.messaging;

import br.com.redestudio.dtos.CloudStatePayload;

/** Payload published to the {@code project.cloud-snapshot} RabbitMQ queue (autosave of a CLOUD project). */
public record ProjectCloudSnapshotMessage(String projectId, String ownerId, CloudStatePayload cloudState) {
}
