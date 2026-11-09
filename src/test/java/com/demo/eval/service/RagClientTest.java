package com.demo.eval.service;

import com.demo.eval.config.EvalProperties;
import com.demo.eval.model.PassMode;
import com.demo.eval.model.RagAnswer;
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
 * RagClient 契约测试：模拟 RAG 的 HTTP 返回，不依赖真实服务。
 */
class RagClientTest {

    private static final String BASE_URL = "http://rag.test";

    private MockRestServiceServer server;
    private RagClient client;

    @BeforeEach
    void setUp() {
        EvalProperties properties = new EvalProperties(
                BASE_URL,
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                0.6,
                "http://agent.test",
                Duration.ofSeconds(5),
                PassMode.HYBRID);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RagClient(properties, builder.build());
    }

    @Test
    void shouldParseAnswerAndSources() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"question": "年假多少天？"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "message": "success",
                          "data": {
                            "answer": "年假十天。",
                            "sources": [
                              {"documentName": "员工手册.txt", "snippet": "年假十天", "score": 0.93}
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RagAnswer answer = client.chat("年假多少天？");

        assertThat(answer.answer()).contains("十天");
        assertThat(answer.sources()).hasSize(1);
        assertThat(answer.sources().get(0).documentName()).isEqualTo("员工手册.txt");
        server.verify();
    }

    @Test
    void shouldThrowWhenRagReturnsBusinessError() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withSuccess("""
                        {"code": 500, "message": "大模型调用失败", "data": null}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat("年假多少天？"))
                .isInstanceOf(RagCallException.class)
                .hasMessageContaining("code=500")
                .hasMessageContaining("大模型调用失败");
        server.verify();
    }

    @Test
    void shouldThrowWhenRagReturnsHttpError() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.chat("年假多少天？"))
                .isInstanceOf(RagCallException.class)
                .hasMessageContaining("HTTP 502");
        server.verify();
    }
}
