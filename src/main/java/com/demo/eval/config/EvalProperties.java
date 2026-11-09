package com.demo.eval.config;

import com.demo.eval.model.PassMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 评测平台配置（绑定 application.yml 中 eval.* 前缀）。
 */
@ConfigurationProperties(prefix = "eval")
public record EvalProperties(
        @DefaultValue("http://localhost:8081") String ragBaseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("90s") Duration readTimeout,
        @DefaultValue("0.6") double passThreshold,
        @DefaultValue("http://localhost:8080") String agentBaseUrl,
        @DefaultValue("120s") Duration agentReadTimeout,
        @DefaultValue("HYBRID") PassMode passMode
) {
}
