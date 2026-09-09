package br.com.redestudio.messaging;

import br.com.redestudio.dtos.NetworkStatePayload;

/** Payload published to the {@code project.snapshot} RabbitMQ queue (autosave). */
public record ProjectSnapshotMessage(String projectId, String ownerId, NetworkStatePayload networkState) {
}
