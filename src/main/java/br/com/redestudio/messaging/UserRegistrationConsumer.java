package br.com.redestudio.messaging;

import br.com.redestudio.components.RegistrationMailer;
import br.com.redestudio.entities.UserEntity;
import br.com.redestudio.services.UserService;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Consumes {@link UserRegistrationMessage} events and performs the actual
 * MongoDB persistence plus the welcome email.
 *
 * <p>The synchronous request path ({@code AuthService#register}) only claims
 * the Redis idempotency keys before publishing — those keys expire after a
 * short TTL, so this consumer re-validates uniqueness against MongoDB (the
 * durable source of truth, backed by the unique indexes created in
 * {@code StartupRunner}) before persisting, and silently skips duplicates
 * instead of failing the queue.
 */
@ApplicationScoped
public class UserRegistrationConsumer {

    private static final Logger LOG = Logger.getLogger(UserRegistrationConsumer.class);

    @Inject
    UserService userService;

    @Inject
    RegistrationMailer registrationMailer;

    @Incoming("user-registration-in")
    public void consume(JsonObject payload) {
        UserRegistrationMessage message = payload.mapTo(UserRegistrationMessage.class);

        if (userService.existsByEmail(message.email()) || userService.existsByUsername(message.username())) {
            LOG.warnf("Skipping duplicate registration message for email=%s username=%s",
                    message.email(), message.username());
            return;
        }

        UserEntity user = UserEntity.create(
                message.username(), message.email(), message.passwordHash(), message.roles());
        userService.createUser(user);

        // Best-effort: o usuario ja foi persistido com sucesso acima. Uma falha
        // de e-mail aqui nao deve derrubar/reenfileirar a mensagem (o dedup por
        // email/username no topo faria a proxima tentativa pular a criacao E o
        // reenvio do e-mail, silenciosamente). io.quarkus.mailer.Mailer#send nao
        // declara throws -- qualquer falha real (SMTPException do Vert.x
        // incluida) so pode chegar aqui como RuntimeException/Error sem violar
        // esse contrato, entao catch de RuntimeException ja cobre tudo que e
        // alcancavel de verdade (catch de SMTPException direto seria "unreachable
        // catch block" no javac, ja que ela nao esta declarada na assinatura).
        try {
            registrationMailer.sendWelcomeEmail(user.getEmail(), user.getUsername());
        } catch (RuntimeException e) {
            LOG.warnf(e, "Failed to send welcome email to %s — user was created successfully, "
                    + "email delivery will not be retried", user.getEmail());
        }
    }
}
