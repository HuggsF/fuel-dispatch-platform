package com.fueldispatch.dispatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata of the OpenAPI description served at {@code /v3/api-docs} (API-4.1). */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI dispatchServiceOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Dispatch service API")
                                .version("v1")
                                .description(
                                        "Command side of the fuel dispatch platform: create"
                                                + " dispatch orders and move them through"
                                                + " approval, dispatch, delivery or cancellation."
                                                + " Errors follow RFC 9457 with a `code`"
                                                + " property."));
    }
}
