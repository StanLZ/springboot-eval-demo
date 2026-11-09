package com.demo.eval.service;

import com.demo.eval.config.EvalProperties;
import com.demo.eval.model.EvalCase;
import com.demo.eval.model.EvalResult;
import com.demo.eval.model.FailureType;
import com.demo.eval.model.RagAnswer;
import com.demo.eval.model.RagSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标计算单元测试：不依赖 RAG / 网络。
 */
class MetricServiceTest {

    private final MetricService metricService = new MetricService(
            new EvalProperties("http://localhost:8081", Duration.ofSeconds(1), Duration.ofSeconds(5), 0.6));

    @Test
    void shouldPassWhenDocumentHitAndKeywordCovered() {
        EvalCase testCase = new EvalCase(1, "年假多少天？", "十天", List.of("十天"), "员工手册");
        RagAnswer answer = new RagAnswer("根据员工手册，年假为十天。",
                List.of(new RagSource("员工手册.txt", "年假十天", 0.93)));

        EvalResult result = metricService.evaluate(testCase, answer, 120);

        assertThat(result.retrievalHit()).isTrue();
        assertThat(result.keywordCoverage()).isEqualTo(1.0);
        assertThat(result.pass()).isTrue();
        assertThat(result.failureType()).isEqualTo(FailureType.PASS);
        assertThat(result.latencyMs()).isEqualTo(120);
        assertThat(result.score()).isBetween(1, 100);
    }

    @Test
    void shouldMarkRetrievalMiss() {
        EvalCase testCase = new EvalCase(1, "年假多少天？", "十天", List.of("十天"), "员工手册");
        RagAnswer answer = new RagAnswer("年假为十天。",
                List.of(new RagSource("其他文档.pdf", "无关内容", 0.8)));

        EvalResult result = metricService.evaluate(testCase, answer, 80);

        assertThat(result.retrievalHit()).isFalse();
        assertThat(result.pass()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.RETRIEVAL_MISS);
    }

    @Test
    void shouldMarkAnswerIncompleteWhenCoverageBelowThreshold() {
        EvalCase testCase = new EvalCase(1, "年假多少天？", "十天", List.of("十天", "年假"), "员工手册");
        RagAnswer answer = new RagAnswer("十天。",
                List.of(new RagSource("员工手册.txt", "年假十天", 0.93)));

        EvalResult result = metricService.evaluate(testCase, answer, 90);

        assertThat(result.retrievalHit()).isTrue();
        assertThat(result.keywordCoverage()).isEqualTo(0.5);
        assertThat(result.pass()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.ANSWER_INCOMPLETE);
    }

    @Test
    void shouldBuildTargetErrorResult() {
        EvalCase testCase = new EvalCase(1, "年假多少天？", "十天", List.of("十天"), "员工手册");

        EvalResult result = metricService.targetError(testCase, 500, "无法连接 RAG 服务");

        assertThat(result.pass()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.TARGET_ERROR);
        assertThat(result.error()).contains("无法连接");
        assertThat(result.answer()).isEmpty();
        assertThat(result.sources()).isEmpty();
    }
}
