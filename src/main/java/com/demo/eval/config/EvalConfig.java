package com.demo.eval.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 评测平台 HTTP 客户端：
 *  - ragRestClient：调用被测 RAG /api/chat
 *  - agentRestClient：调用被测 Agent /api/agent/chat
 *  - judgeRestClient：调用智谱 OpenAI 兼容接口
 */
@Configuration
@EnableConfigurationProperties({EvalProperties.class, JudgeProperties.class})
public class EvalConfig {

    @Bean("ragRestClient")
    public RestClient ragRestClient(EvalProperties properties, RestClient.Builder builder) {
        return client(builder, properties.ragBaseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    @Bean("agentRestClient")
    public RestClient agentRestClient(EvalProperties properties, RestClient.Builder builder) {
        return client(builder, properties.agentBaseUrl(), properties.connectTimeout(), properties.agentReadTimeout());
    }

    @Bean("judgeRestClient")
    public RestClient judgeRestClient(JudgeProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = factory(properties.timeout());
        RestClient.Builder configured = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(factory);
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        return configured.build();
    }

    private static RestClient client(RestClient.Builder builder, String baseUrl,
                                     Duration connectTimeout, Duration readTimeout) {
        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory(connectTimeout, readTimeout))
                .build();
    }

    private static SimpleClientHttpRequestFactory factory(Duration timeout) {
        return factory(timeout, timeout);
    }

    private static SimpleClientHttpRequestFactory factory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(toMillis(connectTimeout));
        factory.setReadTimeout(toMillis(readTimeout));
        return factory;
    }

    private static int toMillis(Duration duration) {
        return Math.toIntExact(duration.toMillis());
    }
}
