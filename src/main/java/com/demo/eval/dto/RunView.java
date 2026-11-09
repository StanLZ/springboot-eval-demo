package com.demo.eval.dto;

import com.demo.eval.model.RunStatus;
import com.demo.eval.model.RunSummary;

import java.time.Instant;

/**
 * 评测运行视图：进度、状态与汇总。
 */
public record RunView(
        long id,
        long datasetId,
        String datasetName,
        RunStatus status,
        int total,
        int completed,
        Instant startedAt,
        Instant finishedAt,
        RunSummary summary
) {
}
