package com.example.kiwoom.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfig {
    /** 종목 목록(ka10099) 응답은 전체 시장 종목을 포함해 기본 버퍼(256KB)를 초과할 수 있어 넉넉하게 설정합니다. */
    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024;

    @Bean
    public WebClient webClient(KiwoomApiProperties properties) {
        ConnectionProvider connectionProvider =
                ConnectionProvider.builder("kiwoom")
                        .maxConnections(properties.maxConnections())
                        .build();
        HttpClient httpClient =
                HttpClient.create(connectionProvider)
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                Math.toIntExact(properties.connectTimeout().toMillis()))
                        .responseTimeout(properties.responseTimeout());

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(
                        configurer ->
                                configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }
}
