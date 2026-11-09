package com.demo.eval.model;

/**
 * RAG 返回的一条来源引用。
 */
public record RagSource(
        String documentName,
        String snippet,
        double score
) {
}
