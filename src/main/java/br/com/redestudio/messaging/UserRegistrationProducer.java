package br.com.redestudio.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Publishes {@link UserRegistrationMessage} events to the {@code user.registration}
 * RabbitMQ queue. Consumed asynchronously by {@link UserRegistrationConsumer}.
 */
@ApplicationScoped
public class UserRegistrationProducer {

    @Channel("user-registration-out")
    Emitter<UserRegistrationMessage> emitter;

    public void publish(UserRegistrationMessage message) {
        emitter.send(message);
    }
}
