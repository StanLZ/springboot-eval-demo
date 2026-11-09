package com.demo.eval.service;

import com.demo.eval.config.EvalProperties;
import com.demo.eval.model.EvalCase;
import com.demo.eval.model.EvalResult;
import com.demo.eval.model.FailureType;
import com.demo.eval.model.RagAnswer;
import com.demo.eval.model.RagSource;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 评测指标计算：
 *  - Retrieval Hit：期望来源文档是否出现在 RAG sources 中；
 *  - Keyword Coverage：期望关键词在答案中的覆盖率；
 *  - Answer Similarity：答案与参考答案的字符 bigram Dice；
 *  - 综合分与失败归因。
 */
@Service
public class MetricService {

    private final double passThreshold;

    public MetricService(EvalProperties properties) {
        this.passThreshold = properties.passThreshold();
    }

    public EvalResult evaluate(EvalCase testCase, RagAnswer ragAnswer, long latencyMs) {
        String answer = ragAnswer.answer();
        List<RagSource> sources = ragAnswer.sources() == null ? List.of() : ragAnswer.sources();

        Boolean retrievalHit = retrievalHit(testCase.expectedDocument(), sources);
        double keywordCoverage = keywordCoverage(testCase.expectedKeywords(), answer);
        double similarity = bigramDice(answer, testCase.referenceAnswer());
        int score = (int) Math.round((keywordCoverage * 0.6 + similarity * 0.4) * 100);

        boolean pass = (retrievalHit == null || retrievalHit) && keywordCoverage >= passThreshold;
        FailureType failureType;
        if (pass) {
            failureType = FailureType.PASS;
        } else if (Boolean.FALSE.equals(retrievalHit)) {
            failureType = FailureType.RETRIEVAL_MISS;
        } else {
            failureType = FailureType.ANSWER_INCOMPLETE;
        }

        return new EvalResult(
                testCase.id(),
                testCase.question(),
                testCase.referenceAnswer(),
                testCase.expectedKeywords(),
                testCase.expectedDocument(),
                answer,
                sources,
                retrievalHit,
                round(keywordCoverage),
                round(similarity),
                Math.max(0, Math.min(100, score)),
                latencyMs,
                pass,
                failureType,
                null
        );
    }

    /** 被测系统异常：保留用例信息，生成 TARGET_ERROR 结果，不让整轮评测中断 */
    public EvalResult targetError(EvalCase testCase, long latencyMs, String error) {
        return new EvalResult(
                testCase.id(),
                testCase.question(),
                testCase.referenceAnswer(),
                testCase.expectedKeywords(),
                testCase.expectedDocument(),
                "",
                List.of(),
                null,
                0,
                0,
                0,
                latencyMs,
                false,
                FailureType.TARGET_ERROR,
                error
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
