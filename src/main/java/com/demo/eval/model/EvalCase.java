package com.demo.eval.model;

import java.util.List;

/**
 * 一条评测用例。
 *
 * @param id               用例 ID
 * @param question         提问
 * @param referenceAnswer  参考答案
 * @param expectedKeywords 期望出现在答案中的关键词
 * @param expectedDocument 期望命中的来源文档名（可为空，为空时不计算检索命中）
 */
public record EvalCase(
        long id,
        String question,
        String referenceAnswer,
        List<String> expectedKeywords,
        String expectedDocument
) {
}
