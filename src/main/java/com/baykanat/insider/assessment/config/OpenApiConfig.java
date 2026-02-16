package com.baykanat.insider.assessment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** OpenAPI / Swagger UI bean tanımı. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI insiderOneOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Insider One - Event Ingestion & Metrics API")
                        .description("""
                                High-throughput event ingestion platform that accepts raw event data \
                                via HTTP API, stores it in PostgreSQL, and exposes aggregated metrics. \
                                Designed for ~2,000 events/sec average and ~20,000 events/sec peak load.\
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Burak Aykanat")
                                .email("burak.aykanat12@gmail.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development")
                ));
    }
}
