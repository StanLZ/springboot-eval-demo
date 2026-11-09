package com.demo.eval.model;

/**
 * 一次评测运行的汇总指标。
 *
 * @param retrievalHitRate 只统计配置了 expectedDocument 的用例；没有这类用例时为 null
 */
public record RunSummary(
        int total,
        int completed,
        int passed,
        double passRate,
        Double retrievalHitRate,
        double avgScore,
        double avgSimilarity,
        double avgLatencyMs
) {
}
