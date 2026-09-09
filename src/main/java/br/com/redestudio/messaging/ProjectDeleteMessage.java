package br.com.redestudio.messaging;

/** Payload published to the {@code project.delete} RabbitMQ queue. */
public record ProjectDeleteMessage(String projectId, String ownerId) {
}
