package io.github.nicolassanchez1.technicaltestdavivienda.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI documentsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Buscador y Visor de Documentos Tecnicos")
                        .version("0.1.0")
                        .description("Carga, indexacion asincrona, busqueda full-text y visualizacion de documentos"));
    }
}
