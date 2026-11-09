package com.demo.eval.service;

import com.demo.eval.config.EvalProperties;
import com.demo.eval.config.JudgeProperties;
import com.demo.eval.model.AgentAnswer;
import com.demo.eval.model.EvalCase;
import com.demo.eval.model.EvalResult;
import com.demo.eval.model.EvalTargetType;
import com.demo.eval.model.FailureType;
import com.demo.eval.model.JudgeOutcome;
import com.demo.eval.model.JudgeStatus;
import com.demo.eval.model.PassMode;
import com.demo.eval.model.RagAnswer;
import com.demo.eval.model.RagSource;
import com.demo.eval.model.ToolCall;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标计算单元测试：覆盖 RAG、Agent、judge 与失败归因，不依赖网络。
 */
class MetricServiceTest {

    private final MetricService metricService = new MetricService(
            new EvalProperties(
                    "http://localhost:8081",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(5),
                    0.6,
                    "http://localhost:8080",
                    Duration.ofSeconds(10),
                    PassMode.HYBRID),
            new JudgeProperties(
                    true,
                    "http://judge.test",
                    "test-key",
                    "glm-4.7",
                    Duration.ofSeconds(5),
                    0.0,
                    800,
                    "disabled",
                    70));

    private final JudgeOutcome goodJudge = new JudgeOutcome(
            JudgeStatus.OK, 90, "答案正确", "glm-4.7", null);

    @Test
    void ragShouldPassWhenDocumentHitAndKeywordCovered() {
        EvalCase testCase = new EvalCase(
                1, "年假多少天？", "十天", List.of("十天"), "员工手册", null);
        RagAnswer answer = new RagAnswer("根据员工手册，年假为十天。",
                List.of(new RagSource("员工手册.txt", "年假十天", 0.93)));

        EvalResult result = metricService.evaluateRag(testCase, answer, goodJudge, 120, 30);

        assertThat(result.targetType()).isEqualTo(EvalTargetType.RAG);
        assertThat(result.retrievalHit()).isTrue();
        assertThat(result.keywordCoverage()).isEqualTo(1.0);
        assertThat(result.pass()).isTrue();
        assertThat(result.failureType()).isEqualTo(FailureType.PASS);
        assertThat(result.score()).isEqualTo(90);
        assertThat(result.ruleScore()).isBetween(1, 100);
        assertThat(result.judgeStatus()).isEqualTo(JudgeStatus.OK);
        assertThat(result.latencyMs()).isEqualTo(120);
        assertThat(result.judgeLatencyMs()).isEqualTo(30);
    }

    @Test
    void ragShouldMarkRetrievalMiss() {
        EvalCase testCase = new EvalCase(
                1, "年假多少天？", "十天", List.of("十天"), "员工手册", null);
        RagAnswer answer = new RagAnswer("年假为十天。",
                List.of(new RagSource("其他文档.pdf", "无关内容", 0.8)));

        EvalResult result = metricService.evaluateRag(testCase, answer, goodJudge, 80, 20);

        assertThat(result.retrievalHit()).isFalse();
        assertThat(result.pass()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.RETRIEVAL_MISS);
    }

    @Test
    void ragShouldMarkAnswerIncomplete() {
        EvalCase testCase = new EvalCase(
                1, "年假多少天？", "十天", List.of("十天", "年假"), "员工手册", null);
        RagAnswer answer = new RagAnswer("十天。",
                List.of(new RagSource("员工手册.txt", "年假十天", 0.93)));

        EvalResult result = metricService.evaluateRag(testCase, answer, goodJudge, 90, 20);

        assertThat(result.retrievalHit()).isTrue();
        assertThat(result.keywordCoverage()).isEqualTo(0.5);
        assertThat(result.pass()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.ANSWER_INCOMPLETE);
    }

    @Test
    void hybridShouldFailWhenJudgeScoreBelowThreshold() {
        EvalCase testCase = new EvalCase(
                1, "年假多少天？", "十天", List.of("十天"), "员工手册", null);
        RagAnswer answer = new RagAnswer("年假十天。",
                List.of(new RagSource("员工手册.txt", "年假十天", 0.93)));
        JudgeOutcome lowJudge = new JudgeOutcome(
                JudgeStatus.OK, 40, "答案与上下文冲突", "glm-4.7", null);

        EvalResult result = metricService.evaluateRag(testCase, answer, lowJudge, 100, 25);

        assertThat(result.pass()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.JUDGE_FAIL);
        assertThat(result.score()).isEqualTo(40);
        assertThat(result.ruleScore()).isGreaterThan(40);
    }

    @Test
    void skippedJudgeShouldFallbackToRuleMetrics() {
        EvalCase testCase = new EvalCase(
                1, "年假多少天？", "十天", List.of("十天"), "员工手册", null);
        RagAnswer answer = new RagAnswer("年假十天。",
                List.of(new RagSource("员工手册.txt", "年假十天", 0.93)));

        EvalResult result = metricService.evaluateRag(
                testCase, answer, JudgeOutcome.skipped("glm-4.7"), 100, 0);

        assertThat(result.pass()).isTrue();
        assertThat(result.judgeStatus()).isEqualTo(JudgeStatus.SKIPPED);
        assertThat(result.score()).isEqualTo(result.ruleScore());
        assertThat(result.judgeScore()).isNull();
    }

    @Test
    void agentShouldPassWhenToolHitAndAnswerCorrect() {
        EvalCase testCase = new EvalCase(
                2, "计算 (12.5+7.5)*2", "40", List.of("40"), null, "calculator");
        AgentAnswer answer = new AgentAnswer("40", "SUCCESS",
                List.of(new ToolCall("calculator", "{\"num1\":20}", "20 * 2 = 40")));

        EvalResult result = metricService.evaluateAgent(testCase, answer, goodJudge, 500, 30);

        assertThat(result.targetType()).isEqualTo(EvalTargetType.AGENT);
        assertThat(result.agentStatus()).isEqualTo("SUCCESS");
        assertThat(result.toolHit()).isTrue();
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.pass()).isTrue();
        assertThat(result.failureType()).isEqualTo(FailureType.PASS);
    }

    @Test
    void agentShouldMarkToolMiss() {
        EvalCase testCase = new EvalCase(
                2, "计算 (12.5+7.5)*2", "40", List.of("40"), null, "calculator");
        AgentAnswer answer = new AgentAnswer("40", "SUCCESS",
                List.of(new ToolCall("text_stats", "{}", "4")));

        EvalResult result = metricService.evaluateAgent(
                testCase, answer, JudgeOutcome.skipped("glm-4.7"), 500, 0);

        assertThat(result.toolHit()).isFalse();
        assertThat(result.pass()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.TOOL_MISS);
    }

    @Test
    void agentShouldMarkAgentError() {
        EvalCase testCase = new EvalCase(
                2, "计算 (12.5+7.5)*2", "40", List.of("40"), null, "calculator");
        AgentAnswer answer = new AgentAnswer("抱歉，模型服务不可用", "LLM_ERROR", List.of());

        EvalResult result = metricService.evaluateAgent(
                testCase, answer, JudgeOutcome.skipped("glm-4.7"), 500, 0);

        assertThat(result.pass()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.AGENT_ERROR);
    }

    @Test
    void shouldBuildTargetErrorResult() {
        EvalCase testCase = new EvalCase(
                1, "年假多少天？", "十天", List.of("十天"), "员工手册", null);

        EvalResult result = metricService.targetError(
                testCase, EvalTargetType.RAG, 500, "无法连接 RAG 服务");

        assertThat(result.pass()).isFalse();
        assertThat(result.failureType()).isEqualTo(FailureType.TARGET_ERROR);
        assertThat(result.error()).contains("无法连接");
        assertThat(result.answer()).isEmpty();
        assertThat(result.sources()).isEmpty();
    }
}
