package com.demo.eval.dto;

/**
 * 数据集列表项。
 */
public record DatasetView(
        long id,
        String name,
        String description,
        int caseCount
) {
}
