package com.bondhub.searchservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                                .email("dev@bondhub.com")));
    }
}
