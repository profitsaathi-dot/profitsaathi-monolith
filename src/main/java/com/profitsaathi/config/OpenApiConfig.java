package com.profitsaathi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Springdoc bean — defines the OpenAPI document's metadata and registers a
 * "bearerAuth" security scheme so the Swagger UI's Authorize button accepts
 * a JWT access token. Every protected endpoint inherits the requirement via
 * {@link SecurityRequirement} below.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI profitSaathiOpenAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        // nginx exposes the monolith under /spring/*. Without this Server
        // entry, Swagger UI's "Try it out" sends requests to the bare
        // /api/v1/... path and gets a 404 from nginx (which only routes
        // /spring/*). Pinning a relative server URL prepends the prefix.
        Server gateway = new Server()
                .url("/spring")
                .description("nginx-fronted gateway (default)");
        Server direct = new Server()
                .url("/")
                .description("Direct Spring Boot — bypass nginx (port 9097)");

        return new OpenAPI()
                .info(new Info()
                        .title("ProfitSaathi Monolith API")
                        .version("v1")
                        .description("REST API surface of the ProfitSaathi monolith — "
                                + "auth, sellers, customers, admins, products, orders, "
                                + "payments, OTP, WhatsApp.")
                        .contact(new Contact().name("ProfitSaathi Platform")))
                .servers(List.of(gateway, direct))
                .components(new Components().addSecuritySchemes("bearerAuth", bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
