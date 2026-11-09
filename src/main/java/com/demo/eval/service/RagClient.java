package com.demo.eval.service;

import com.demo.eval.config.EvalProperties;
import com.demo.eval.model.RagAnswer;
import com.demo.eval.model.RagSource;
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
 * 被测 RAG 服务客户端。
 * 只依赖 RAG 的 HTTP 契约：POST /api/chat {question} → {code, message, data:{answer, sources}}。
 */
@Component
public class RagClient {

    private static final Logger log = LoggerFactory.getLogger(RagClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public RagClient(EvalProperties properties,
                     @Qualifier("ragRestClient") RestClient ragRestClient) {
        this.restClient = ragRestClient;
        this.baseUrl = properties.ragBaseUrl();
    }

    public RagAnswer chat(String question) {
        RagApiResponse response;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("question", question))
                    .retrieve()
                    .body(RagApiResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("RAG 返回 HTTP {}：{}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new RagCallException("RAG 服务返回 HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            log.warn("无法连接 RAG 服务 {}：{}", baseUrl, e.getMessage());
            throw new RagCallException("无法连接 RAG 服务（" + baseUrl + "），请确认 RAG 已启动", e);
        } catch (RestClientException e) {
            log.warn("调用 RAG 服务失败：{}", e.getMessage());
            throw new RagCallException("调用 RAG 服务失败：" + e.getMessage(), e);
        }

        if (response == null) {
            throw new RagCallException("RAG 服务返回空响应");
        }
        if (response.code() != 0) {
            throw new RagCallException("RAG 返回业务错误（code=" + response.code() + "）："
                    + (response.message() == null ? "未知错误" : response.message()));
        }
        if (response.data() == null || response.data().answer() == null
                || response.data().answer().isBlank()) {
            throw new RagCallException("RAG 未返回有效答案");
        }
        List<RagSource> sources = response.data().sources() == null
                ? List.of()
                : response.data().sources();
        return new RagAnswer(response.data().answer(), sources);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RagApiResponse(int code, String message, RagData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RagData(String answer, List<RagSource> sources) {
    }
}
