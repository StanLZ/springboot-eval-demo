package com.demo.eval.service;

import com.demo.eval.config.JudgeProperties;
import com.demo.eval.model.EvalCase;
import com.demo.eval.model.JudgeOutcome;
import com.demo.eval.model.JudgeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * JudgeService 契约测试：模拟智谱 OpenAI 兼容接口，不访问真实 API。
 */
class JudgeServiceTest {

    private static final String BASE_URL = "http://judge.test/api/paas/v4";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private JudgeService judgeService;
    private JudgeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JudgeProperties(
                true,
                BASE_URL,
                "test-key",
                "glm-4.7",
                Duration.ofSeconds(5),
                0.0,
                800,
                "disabled",
                70);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        judgeService = new JudgeService(properties, builder.build(), objectMapper);
    }

    @Test
    void shouldParseJudgeScoreAndReason() throws Exception {
        String judgeJson = "{\"correctness\":38,\"faithfulness\":27,\"completeness\":23,"
                + "\"score\":88,\"pass\":true,\"reason\":\"答案与参考答案一致\"}";
        String response = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", judgeJson)))));

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(POST))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        JudgeOutcome outcome = judgeService.judge(testCase(), "年假十天。", List.of("员工手册：年假十天"));

        assertThat(outcome.status()).isEqualTo(JudgeStatus.OK);
        assertThat(outcome.score()).isEqualTo(88);
        assertThat(outcome.reason()).contains("一致");
        assertThat(outcome.model()).isEqualTo("glm-4.7");
        server.verify();
    }

    @Test
    void shouldReturnErrorWhenJudgeReturnsInvalidJson() throws Exception {
        String response = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "not json")))));

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        JudgeOutcome outcome = judgeService.judge(testCase(), "年假十天。", List.of());

        assertThat(outcome.status()).isEqualTo(JudgeStatus.ERROR);
        assertThat(outcome.error()).isNotBlank();
    }

    @Test
    void shouldReturnErrorWhenJudgeReturnsHttpError() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withServerError());

        JudgeOutcome outcome = judgeService.judge(testCase(), "年假十天。", List.of());

        assertThat(outcome.status()).isEqualTo(JudgeStatus.ERROR);
        assertThat(outcome.error()).contains("HTTP 500");
    }

    @Test
    void shouldSkipWhenApiKeyIsBlank() {
        JudgeProperties disabled = new JudgeProperties(
                true, BASE_URL, "", "glm-4.7", Duration.ofSeconds(5), 0.0, 800, "disabled", 70);
        JudgeService service = new JudgeService(disabled, RestClient.builder().build(), objectMapper);

        JudgeOutcome outcome = service.judge(testCase(), "年假十天。", List.of());

        assertThat(outcome.status()).isEqualTo(JudgeStatus.SKIPPED);
        assertThat(outcome.score()).isNull();
        assertThat(service.enabled()).isFalse();
    }

    private EvalCase testCase() {
        return new EvalCase(
                1,
                "年假多少天？",
                "十天",
                List.of("十天"),
                "员工手册",
                null);
    }
}
