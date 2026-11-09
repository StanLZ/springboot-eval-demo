package com.demo.eval.service;

import com.demo.eval.config.JudgeProperties;
import com.demo.eval.model.EvalCase;
import com.demo.eval.model.JudgeOutcome;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM-as-judge：调用智谱 OpenAI 兼容接口，对模型回答做语义评分。
 *
 * <p>默认使用 glm-4.7 并关闭 thinking：
 *  - 200K 上下文足够容纳问题 + 参考答案 + 检索片段 + 回答；
 *  - 关闭 thinking 后延迟和输出 token 更可控；
 *  - 支持 JSON 结构化输出。
 *
 * <p>Judge 失败不会中断评测：返回 ERROR/SKIPPED，由指标层回退到规则指标。
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private static final int MAX_CONTEXT_CHARS = 3000;
    private static final int MAX_ANSWER_CHARS = 2000;
    private static final int MAX_REFERENCE_CHARS = 1000;

    private final JudgeProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public JudgeService(JudgeProperties properties,
                        @Qualifier("judgeRestClient") RestClient judgeRestClient,
                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = judgeRestClient;
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return properties.enabled()
                && properties.apiKey() != null
                && !properties.apiKey().isBlank();
    }

    /**
     * 对一条回答打分。永远不抛异常：失败返回 JudgeOutcome.error。
     */
    public JudgeOutcome judge(EvalCase testCase, String answer, List<String> contexts) {
        String model = properties.model();
        if (!enabled()) {
            return JudgeOutcome.skipped(model);
        }

        try {
            Map<String, Object> body = buildRequestBody(testCase, answer, contexts);
            ChatCompletion response = restClient.post()
                    .uri(chatCompletionsUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatCompletion.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return JudgeOutcome.error(model, "judge 返回空响应");
            }
            String content = response.choices().get(0).message() == null
                    ? null
                    : response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                return JudgeOutcome.error(model, "judge 返回空内容");
            }

            JsonNode json = objectMapper.readTree(extractJson(content));
            int score = json.path("score").asInt(-1);
            if (score < 0) {
                // 兼容只返回三个子维度、没有总分的模型输出
                score = json.path("correctness").asInt(0)
                        + json.path("faithfulness").asInt(0)
                        + json.path("completeness").asInt(0);
            }
            if (score < 0 || score > 100) {
                return JudgeOutcome.error(model, "judge 分数非法：" + score);
            }
            String reason = json.path("reason").asText("");
            return new JudgeOutcome(com.demo.eval.model.JudgeStatus.OK, score, reason, model, null);
        } catch (RestClientResponseException e) {
            log.warn("Judge 返回 HTTP {}：{}", e.getStatusCode().value(), e.getResponseBodyAsString());
            return JudgeOutcome.error(model, "judge HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            log.warn("Judge 连接失败：{}", e.getMessage());
            return JudgeOutcome.error(model, "judge 连接失败：" + e.getMessage());
        } catch (RestClientException e) {
            log.warn("Judge 调用失败：{}", e.getMessage());
            return JudgeOutcome.error(model, "judge 调用失败：" + e.getMessage());
        } catch (Exception e) {
            log.warn("Judge 解析失败：{}", e.getMessage());
            return JudgeOutcome.error(model, "judge 结果解析失败：" + e.getMessage());
        }
    }

    private String chatCompletionsUrl() {
        return properties.baseUrl().replaceAll("/+$", "") + "/chat/completions";
    }

    private Map<String, Object> buildRequestBody(EvalCase testCase, String answer, List<String> contexts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是严格的中文答案评测员，只输出 JSON，不要输出 markdown 代码块。"),
                Map.of("role", "user", "content", buildPrompt(testCase, answer, contexts))
        ));
        body.put("temperature", properties.temperature());
        body.put("max_tokens", properties.maxTokens());
        body.put("response_format", Map.of("type", "json_object"));

        if (properties.model().startsWith("glm-5.3")) {
            // GLM-5.3 强制思考，不支持 disabled；用 low 控制成本
            body.put("thinking", Map.of("type", "enabled"));
            body.put("reasoning_effort", "low");
        } else {
            String type = "enabled".equalsIgnoreCase(properties.thinking()) ? "enabled" : "disabled";
            body.put("thinking", Map.of("type", type));
        }
        return body;
    }

    private String buildPrompt(EvalCase testCase, String answer, List<String> contexts) {
        String contextText = contexts == null || contexts.isEmpty()
                ? "（无额外上下文）"
                : String.join("\n---\n", contexts);
        return """
                请根据以下信息，对【模型回答】做严格评分。

                【问题】
                %s

                【参考答案】
                %s

                【评测上下文】
                %s

                【模型回答】
                %s

                评分维度：
                - correctness（0-40）：与参考答案的事实一致性；
                - faithfulness（0-30）：回答是否被上下文支持，是否编造；
                - completeness（0-30）：是否覆盖参考答案的关键点。

                只输出一个 JSON 对象，不要输出任何其他文字：
                {"correctness": 0, "faithfulness": 0, "completeness": 0, "score": 0, "pass": true, "reason": "..."}
                其中 score 为 0-100 的总分，pass 为是否达到 70 分以上。
                """.formatted(
                truncate(testCase.question(), MAX_CONTEXT_CHARS),
                truncate(testCase.referenceAnswer(), MAX_REFERENCE_CHARS),
                truncate(contextText, MAX_CONTEXT_CHARS),
                truncate(answer, MAX_ANSWER_CHARS)
        );
    }

    private String extractJson(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "…";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletion(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String content) {
    }
}
