package com.demo.eval.model;

import java.util.List;

/**
 * 单条用例的评测结果。
 *
 * @param retrievalHit     是否命中期望来源；null 表示该用例没有配置期望文档
 * @param keywordCoverage  期望关键词覆盖率 0~1
 * @param similarity       与参考答案的字符 bigram Dice 相似度 0~1
 * @param score            综合分 0~100
 * @param pass             是否通过
 * @param failureType      失败类型（PASS 表示通过）
 * @param error            被测系统异常时的错误信息
 */
public record EvalResult(
        long caseId,
        String question,
        String referenceAnswer,
        List<String> expectedKeywords,
        String expectedDocument,
        String answer,
        List<RagSource> sources,
        Boolean retrievalHit,
        double keywordCoverage,
        double similarity,
        int score,
        long latencyMs,
        boolean pass,
        FailureType failureType,
        String error
) {
}
