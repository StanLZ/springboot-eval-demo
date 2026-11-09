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
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 评测指标计算：
 *  - RAG：Retrieval Hit、Keyword Coverage、Answer Similarity；
 *  - Agent：Agent Status、Tool Hit、Keyword Coverage、Answer Similarity；
 *  - Judge：语义分数，作为 HYBRID 模式的第二层判定；
 *  - 失败归因。
 */
@Service
public class MetricService {

    private final double passThreshold;
    private final int judgePassScore;
    private final PassMode passMode;

    public MetricService(EvalProperties evalProperties, JudgeProperties judgeProperties) {
        this.passThreshold = evalProperties.passThreshold();
        this.passMode = evalProperties.passMode();
        this.judgePassScore = judgeProperties.passScore();
    }

    public EvalResult evaluateRag(EvalCase testCase, RagAnswer ragAnswer,
                                  JudgeOutcome judge, long latencyMs, long judgeLatencyMs) {
        List<RagSource> sources = ragAnswer.sources() == null ? List.of() : ragAnswer.sources();
        Boolean retrievalHit = retrievalHit(testCase.expectedDocument(), sources);
        double coverage = keywordCoverage(testCase.expectedKeywords(), ragAnswer.answer());
        double similarity = bigramDice(ragAnswer.answer(), testCase.referenceAnswer());
        int ruleScore = ruleScore(coverage, similarity);

        FailureType baseFailure;
        if (Boolean.FALSE.equals(retrievalHit)) {
            baseFailure = FailureType.RETRIEVAL_MISS;
        } else if (coverage < passThreshold) {
            baseFailure = FailureType.ANSWER_INCOMPLETE;
        } else {
            baseFailure = FailureType.PASS;
        }

        return buildResult(
                testCase,
                EvalTargetType.RAG,
                ragAnswer.answer(),
                sources,
                List.of(),
                null,
                retrievalHit,
                null,
                coverage,
                similarity,
                ruleScore,
                judge,
                latencyMs,
                judgeLatencyMs,
                baseFailure
        );
    }

    public EvalResult evaluateAgent(EvalCase testCase, AgentAnswer agentAnswer,
                                    JudgeOutcome judge, long latencyMs, long judgeLatencyMs) {
        List<ToolCall> toolCalls = agentAnswer.toolCalls() == null ? List.of() : agentAnswer.toolCalls();
        Boolean toolHit = toolHit(testCase.expectedTool(), toolCalls);
        double coverage = keywordCoverage(testCase.expectedKeywords(), agentAnswer.answer());
        double similarity = bigramDice(agentAnswer.answer(), testCase.referenceAnswer());
        int ruleScore = ruleScore(coverage, similarity);
        String agentStatus = agentAnswer.status() == null ? "UNKNOWN" : agentAnswer.status();

        FailureType baseFailure;
        if (!"SUCCESS".equalsIgnoreCase(agentStatus)) {
            baseFailure = FailureType.AGENT_ERROR;
        } else if (Boolean.FALSE.equals(toolHit)) {
            baseFailure = FailureType.TOOL_MISS;
        } else if (coverage < passThreshold) {
            baseFailure = FailureType.ANSWER_INCOMPLETE;
        } else {
            baseFailure = FailureType.PASS;
        }

        return buildResult(
                testCase,
                EvalTargetType.AGENT,
                agentAnswer.answer(),
                List.of(),
                toolCalls,
                agentStatus,
                null,
                toolHit,
                coverage,
                similarity,
                ruleScore,
                judge,
                latencyMs,
                judgeLatencyMs,
                baseFailure
        );
    }

    /** 被测系统异常：保留用例信息，生成 TARGET_ERROR 结果，不让整轮评测中断 */
    public EvalResult targetError(EvalCase testCase, EvalTargetType targetType,
                                  long latencyMs, String error) {
        return new EvalResult(
                testCase.id(),
                targetType,
                testCase.question(),
                testCase.referenceAnswer(),
                testCase.expectedKeywords(),
                testCase.expectedDocument(),
                testCase.expectedTool(),
                "",
                List.of(),
                List.of(),
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                JudgeStatus.SKIPPED,
                null,
                null,
                null,
                latencyMs,
                0,
                false,
                FailureType.TARGET_ERROR,
                error
        );
    }

    private EvalResult buildResult(EvalCase testCase,
                                   EvalTargetType targetType,
                                   String answer,
                                   List<RagSource> sources,
                                   List<ToolCall> toolCalls,
                                   String agentStatus,
                                   Boolean retrievalHit,
                                   Boolean toolHit,
                                   double coverage,
                                   double similarity,
                                   int ruleScore,
                                   JudgeOutcome judge,
                                   long latencyMs,
                                   long judgeLatencyMs,
                                   FailureType baseFailure) {
        boolean basePass = baseFailure == FailureType.PASS;
        boolean judgeOk = judge != null && judge.ok();
        boolean pass = basePass;
        FailureType failureType = baseFailure;
        int score = ruleScore;

        if (judgeOk && passMode == PassMode.HYBRID) {
            score = judge.score();
            if (basePass && score < judgePassScore) {
                pass = false;
                failureType = FailureType.JUDGE_FAIL;
            }
        } else {
            // DETERMINISTIC 模式只把 judge 作为参考，不影响通过和有效分
            score = ruleScore;
        }

        JudgeStatus judgeStatus = judge == null ? JudgeStatus.SKIPPED : judge.status();
        return new EvalResult(
                testCase.id(),
                targetType,
                testCase.question(),
                testCase.referenceAnswer(),
                testCase.expectedKeywords(),
                testCase.expectedDocument(),
                testCase.expectedTool(),
                answer,
                sources,
                toolCalls,
                agentStatus,
                retrievalHit,
                toolHit,
                round(coverage),
                round(similarity),
                ruleScore,
                score,
                judgeStatus,
                judge == null ? null : judge.score(),
                judge == null ? null : judge.reason(),
                judge == null ? null : judge.model(),
                latencyMs,
                judgeLatencyMs,
                pass,
                failureType,
                null
        );
    }

    private Boolean retrievalHit(String expectedDocument, List<RagSource> sources) {
        if (expectedDocument == null || expectedDocument.isBlank()) {
            return null;
        }
        String expected = expectedDocument.toLowerCase(Locale.ROOT);
        return sources.stream()
                .map(RagSource::documentName)
                .filter(name -> name != null)
                .anyMatch(name -> name.toLowerCase(Locale.ROOT).contains(expected));
    }

    private Boolean toolHit(String expectedTool, List<ToolCall> toolCalls) {
        if (expectedTool == null || expectedTool.isBlank()) {
            return null;
        }
        return toolCalls.stream()
                .map(ToolCall::name)
                .filter(name -> name != null)
                .anyMatch(name -> name.equalsIgnoreCase(expectedTool));
    }

    private double keywordCoverage(List<String> keywords, String answer) {
        if (keywords == null || keywords.isEmpty()) {
            return 1.0;
        }
        String normalizedAnswer = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        long hit = keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .filter(keyword -> normalizedAnswer.contains(keyword.toLowerCase(Locale.ROOT)))
                .count();
        return (double) hit / keywords.size();
    }

    private int ruleScore(double coverage, double similarity) {
        int score = (int) Math.round((coverage * 0.6 + similarity * 0.4) * 100);
        return Math.max(0, Math.min(100, score));
    }

    /**
     * 字符 bigram Dice 系数，对中文不需要分词：
     * 2 * |A ∩ B| / (|A| + |B|)。
     */
    private double bigramDice(String left, String right) {
        Set<String> leftGrams = grams(left);
        Set<String> rightGrams = grams(right);
        if (leftGrams.isEmpty() && rightGrams.isEmpty()) {
            return 1.0;
        }
        if (leftGrams.isEmpty() || rightGrams.isEmpty()) {
            return 0.0;
        }
        long intersection = leftGrams.stream().filter(rightGrams::contains).count();
        return 2.0 * intersection / (leftGrams.size() + rightGrams.size());
    }

    private Set<String> grams(String text) {
        if (text == null) {
            return Set.of();
        }
        String normalized = text.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
        Set<String> grams = new HashSet<>();
        if (normalized.length() <= 1) {
            if (!normalized.isEmpty()) {
                grams.add(normalized);
            }
            return grams;
        }
        for (int i = 0; i < normalized.length() - 1; i++) {
            grams.add(normalized.substring(i, i + 2));
        }
        return grams;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
