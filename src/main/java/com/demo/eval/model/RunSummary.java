package com.demo.eval.model;

/**
 * 一次评测运行的汇总指标。
 *
 * @param hitRate       RAG 的检索命中率，或 Agent 的工具命中率；没有可统计用例时为 null
 * @param avgJudgeScore 只统计 judge OK 的用例；没有时为 null
 * @param judgeCoverage judge OK 用例数 / 已完成用例数
 */
public record RunSummary(
        int total,
        int completed,
        int passed,
        double passRate,
        Double hitRate,
        double avgScore,
        double avgRuleScore,
        Double avgJudgeScore,
        double judgeCoverage,
        double avgLatencyMs
) {
}
