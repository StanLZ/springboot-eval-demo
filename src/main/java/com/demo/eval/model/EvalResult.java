package com.demo.eval.model;

import java.util.List;

/**
 * 单条用例的评测结果。
 *
 * <p>RAG 用例使用 sources / retrievalHit；Agent 用例使用 toolCalls / toolHit / agentStatus。
 * judge 只记录，不覆盖规则指标，方便对比规则分与 judge 分。
 *
 * @param ruleScore      规则分：关键词覆盖 * 0.6 + 相似度 * 0.4
 * @param score          有效分：judge OK 时取 judgeScore，否则取 ruleScore
 * @param latencyMs      被测系统调用耗时，不含 judge
 * @param judgeLatencyMs judge 调用耗时
 */
public record EvalResult(
        long caseId,
        EvalTargetType targetType,
        String question,
        String referenceAnswer,
        List<String> expectedKeywords,
        String expectedDocument,
        String expectedTool,
        String answer,
        List<RagSource> sources,
        List<ToolCall> toolCalls,
        String agentStatus,
        Boolean retrievalHit,
        Boolean toolHit,
        double keywordCoverage,
        double similarity,
        int ruleScore,
        int score,
        JudgeStatus judgeStatus,
        Integer judgeScore,
        String judgeReason,
        String judgeModel,
        long latencyMs,
        long judgeLatencyMs,
        boolean pass,
        FailureType failureType,
        String error
) {
}
