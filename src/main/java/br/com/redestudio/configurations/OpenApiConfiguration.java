package br.com.redestudio.configurations;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

import jakarta.ws.rs.core.Application;

/**
 * Defines global OpenAPI metadata and the Bearer JWT security scheme.
 *
 * <p>The security scheme declared here ({@code BearerAuth}) must match
 * the {@code @SecurityRequirement(name = "BearerAuth")} used in each
 * protected controller. Swagger UI will render the "Authorize" dialog
 * with a text input for the JWT token when this scheme is present.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Rede Studio API",
                version = "1.0.0",
                description = "API de gerenciamento de infraestrutura de rede corporativa"))
@SecurityScheme(
        securitySchemeName = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Cole o JWT gerado no login: Bearer {token}")
public class OpenApiConfiguration extends Application {
}
