package com.demo.eval.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 评测平台基础 Bean：RAG HTTP 客户端配置。
 */
@Configuration
@EnableConfigurationProperties(EvalProperties.class)
public class EvalConfig {

    @Bean
    public RestClient ragRestClient(EvalProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(toMillis(properties.connectTimeout()));
        factory.setReadTimeout(toMillis(properties.readTimeout()));
        return builder
                .baseUrl(properties.ragBaseUrl())
                .requestFactory(factory)
                .build();
    }

    private static int toMillis(Duration duration) {
        return Math.toIntExact(duration.toMillis());
    }
}
