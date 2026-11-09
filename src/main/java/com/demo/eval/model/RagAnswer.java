package com.demo.eval.model;

import java.util.List;

/**
 * RAG 一次完整问答的结果。
 */
public record RagAnswer(
        String answer,
        List<RagSource> sources
) {
}
