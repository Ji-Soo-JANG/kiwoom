package com.example.kiwoom.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kiwoomOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Kiwoom Stock API")
                                .description("키움 REST API를 이용한 현재가 및 일봉 조회 API")
                                .version("v1"));
    }
}
