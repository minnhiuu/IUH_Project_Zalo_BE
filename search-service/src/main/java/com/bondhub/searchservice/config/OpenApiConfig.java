package com.bondhub.searchservice.config;

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

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI searchServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BondHub Search Service API")
                        .description("Elasticsearch management and search operations for BondHub platform")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BondHub Team")
                                .email("dev@bondhub.com")))
                .servers(List.of(
                        new Server().url("/search").description("Gateway Path"),
                        new Server().url("http://localhost:8086").description("Search Service Direct")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token authentication")));
    }
}
