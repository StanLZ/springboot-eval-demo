package com.demo.eval.service;

import com.demo.eval.config.EvalProperties;
import com.demo.eval.model.AgentAnswer;
import com.demo.eval.model.PassMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * AgentClient 契约测试：模拟 /api/agent/chat 的返回。
 */
class AgentClientTest {

    private static final String BASE_URL = "http://agent.test";

    private MockRestServiceServer server;
    private AgentClient client;

    @BeforeEach
    void setUp() {
        EvalProperties properties = new EvalProperties(
                "http://rag.test",
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                0.6,
                BASE_URL,
                Duration.ofSeconds(5),
                PassMode.HYBRID);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AgentClient(properties, builder.build());
    }

    @Test
    void shouldParseAgentAnswerAndToolCalls() {
        server.expect(requestTo(BASE_URL + "/api/agent/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"sessionId": "eval-1-1", "userQuery": "现在几点？"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "sessionId": "eval-1-1",
                          "answer": "现在是 2026-11-09 20:00:00。",
                          "status": "SUCCESS",
                          "toolCalls": [
                            {"name": "get_current_time", "arguments": "{}", "result": "当前系统时间"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AgentAnswer answer = client.chat("eval-1-1", "现在几点？");

        assertThat(answer.answer()).contains("2026-11-09");
        assertThat(answer.status()).isEqualTo("SUCCESS");
        assertThat(answer.toolCalls()).hasSize(1);
        assertThat(answer.toolCalls().get(0).name()).isEqualTo("get_current_time");
        server.verify();
    }

    @Test
    void shouldThrowWhenAgentReturnsHttpError() {
        server.expect(requestTo(BASE_URL + "/api/agent/chat"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.chat("eval-1-1", "现在几点？"))
                .isInstanceOf(AgentCallException.class)
                .hasMessageContaining("HTTP 502");
        server.verify();
    }
}
