package com.demo.eval.model;

/**
 * LLM judge 的评分结果。
 *
 * @param score 0~100；SKIPPED / ERROR 时为 null
 * @param error 只有 ERROR 时有值
 */
public record JudgeOutcome(
        JudgeStatus status,
        Integer score,
        String reason,
        String model,
        String error
) {

    public static JudgeOutcome skipped(String model) {
        return new JudgeOutcome(JudgeStatus.SKIPPED, null, null, model, null);
    }

    public static JudgeOutcome error(String model, String error) {
        return new JudgeOutcome(JudgeStatus.ERROR, null, null, model, error);
    }

    public boolean ok() {
        return status == JudgeStatus.OK && score != null;
    }
}
