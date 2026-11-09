package com.demo.eval.model;

import java.util.List;

/**
 * 一条评测用例。
 *
 * @param expectedTool Agent 用例期望调用的工具名；RAG 用例为空
 * @param expectedDocument RAG 用例期望命中的来源文档名；Agent 用例为空
 */
public record EvalCase(
        long id,
        String question,
        String referenceAnswer,
        List<String> expectedKeywords,
        String expectedDocument,
        String expectedTool
) {
}
