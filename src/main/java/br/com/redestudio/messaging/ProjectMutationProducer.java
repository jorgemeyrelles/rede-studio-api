package br.com.redestudio.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Publishes project mutation events to their respective RabbitMQ queues —
 * one queue per operation (mirrors {@link UserRegistrationProducer}'s
 * single-purpose-queue style), consolidated into one producer class since
 * all four are the same "project write" concern. Consumed asynchronously by
 * {@link ProjectMutationConsumer}.
 */
@ApplicationScoped
public class ProjectMutationProducer {

    @Channel("project-create-out")
    Emitter<ProjectCreateMessage> createEmitter;

    @Channel("project-rename-out")
    Emitter<ProjectRenameMessage> renameEmitter;

    @Channel("project-snapshot-out")
    Emitter<ProjectSnapshotMessage> snapshotEmitter;

    @Channel("project-delete-out")
    Emitter<ProjectDeleteMessage> deleteEmitter;

    public void publishCreate(ProjectCreateMessage message) {
        createEmitter.send(message);
    }

    public void publishRename(ProjectRenameMessage message) {
        renameEmitter.send(message);
    }

    public void publishSnapshot(ProjectSnapshotMessage message) {
        snapshotEmitter.send(message);
    }

    public void publishDelete(ProjectDeleteMessage message) {
        deleteEmitter.send(message);
    }
}
