package com.demo.eval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * LLM-as-judge 配置（绑定 application.yml 中 eval.judge.* 前缀）。
 *
 * <p>api-key 只从环境变量 ZHIPU_API_KEY 读取，代码与 README 不保存真实密钥。
 * 没有 Key 时 JudgeService 自动返回 SKIPPED，评测继续使用规则指标。
 */
@ConfigurationProperties(prefix = "eval.judge")
public record JudgeProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://open.bigmodel.cn/api/paas/v4") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("glm-4.7") String model,
        @DefaultValue("60s") Duration timeout,
        @DefaultValue("0.0") double temperature,
        @DefaultValue("800") int maxTokens,
        @DefaultValue("disabled") String thinking,
        @DefaultValue("70") int passScore
) {
}
