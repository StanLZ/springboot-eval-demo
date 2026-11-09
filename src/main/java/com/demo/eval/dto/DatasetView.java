package com.demo.eval.dto;

import com.demo.eval.model.EvalTargetType;

/**
 * 数据集列表项。
 */
public record DatasetView(
        long id,
        String name,
        String description,
        EvalTargetType targetType,
        int caseCount
) {
}
