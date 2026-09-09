package br.com.redestudio.messaging;

/** Payload published to the {@code project.rename} RabbitMQ queue. */
public record ProjectRenameMessage(String projectId, String ownerId, String name) {
}
