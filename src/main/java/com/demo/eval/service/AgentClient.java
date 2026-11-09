package com.demo.eval.service;

import com.demo.eval.config.EvalProperties;
import com.demo.eval.model.AgentAnswer;
import com.demo.eval.model.ToolCall;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * 被测 Agent 客户端。
 * 调用现有 POST /api/agent/chat：{sessionId, userQuery} → {answer, status, toolCalls}。
 */
@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public AgentClient(EvalProperties properties,
                       @Qualifier("agentRestClient") RestClient agentRestClient) {
        this.restClient = agentRestClient;
        this.baseUrl = properties.agentBaseUrl();
    }

    public AgentAnswer chat(String sessionId, String question) {
        AgentApiResponse response;
        try {
            response = restClient.post()
                    .uri("/api/agent/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "sessionId", sessionId,
                            "userQuery", question
                    ))
                    .retrieve()
                    .body(AgentApiResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("Agent 返回 HTTP {}：{}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new AgentCallException("Agent 服务返回 HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            log.warn("无法连接 Agent 服务 {}：{}", baseUrl, e.getMessage());
            throw new AgentCallException("无法连接 Agent 服务（" + baseUrl + "），请确认 Agent 已启动", e);
        } catch (RestClientException e) {
            log.warn("调用 Agent 服务失败：{}", e.getMessage());
            throw new AgentCallException("调用 Agent 服务失败：" + e.getMessage(), e);
        }

        if (response == null) {
            throw new AgentCallException("Agent 服务返回空响应");
        }
        if (response.answer() == null) {
            throw new AgentCallException("Agent 未返回 answer");
        }
        String status = response.status() == null ? "UNKNOWN" : response.status();
        List<ToolCall> toolCalls = response.toolCalls() == null ? List.of() : response.toolCalls();
        return new AgentAnswer(response.answer(), status, toolCalls);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AgentApiResponse(
            String sessionId,
            String answer,
            String status,
            List<ToolCall> toolCalls
    ) {
    }
}
