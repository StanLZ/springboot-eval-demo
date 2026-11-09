package com.demo.eval.model;

/**
 * 通过规则模式。
 */
public enum PassMode {
    /** 只看规则指标，judge 只记录不参与通过判定 */
    DETERMINISTIC,
    /** 规则指标做 guardrail，judge 可用时再要求 judgeScore 达标 */
    HYBRID
}
