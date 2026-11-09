package com.demo.eval.dto;

import com.demo.eval.model.EvalCase;

import java.util.List;

/**
 * 数据集详情（含用例）。
 */
public record DatasetDetail(
        long id,
        String name,
        String description,
        List<EvalCase> cases
) {
}
