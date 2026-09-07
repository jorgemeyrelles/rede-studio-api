package br.com.redestudio.components;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Sends the welcome email triggered by a new user registration.
 *
 * <p>In the {@code %dev} profile {@code quarkus.mailer.mock=true} intercepts
 * delivery — messages show up in the Dev UI instead of being sent. In
 * {@code %master} this relays through OCI Email Delivery (SMTP).
 */
@ApplicationScoped
public class RegistrationMailer {

    @Inject
    Mailer mailer;

    public void sendWelcomeEmail(String toEmail, String username) {
        mailer.send(Mail.withText(
                toEmail,
                "Bem-vindo(a) à Rede Studio",
                "Olá " + username + ",\n\n"
                        + "Seu cadastro foi realizado com sucesso.\n\n"
                        + "Equipe Rede Studio"));
    }
}
