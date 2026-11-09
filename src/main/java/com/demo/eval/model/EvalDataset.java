package com.demo.eval.model;

import java.util.List;

/**
 * 评测数据集。
 */
public record EvalDataset(
        long id,
        String name,
        String description,
        List<EvalCase> cases
) {
}
