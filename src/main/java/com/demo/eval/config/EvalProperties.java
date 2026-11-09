package com.demo.eval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 评测平台配置（绑定 application.yml 中 eval.* 前缀）。
 *
 * @param ragBaseUrl     被测 RAG 服务地址
 * @param connectTimeout 连接超时
 * @param readTimeout    读取超时；RAG 内部会调用大模型
 * @param passThreshold  关键词覆盖率通过阈值
 */
@ConfigurationProperties(prefix = "eval")
public record EvalProperties(
        @DefaultValue("http://localhost:8081") String ragBaseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("90s") Duration readTimeout,
        @DefaultValue("0.6") double passThreshold
) {
}
