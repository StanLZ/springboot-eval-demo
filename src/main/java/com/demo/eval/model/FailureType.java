package com.demo.eval.model;

/**
 * 单条用例的失败类型，用于报告里的失败归因。
 */
public enum FailureType {
    PASS,
    /** RAG 检索未命中期望文档 */
    RETRIEVAL_MISS,
    /** Agent 未调用期望工具 */
    TOOL_MISS,
    /** 答案缺少关键信息 */
    ANSWER_INCOMPLETE,
    /** Agent 状态非 SUCCESS */
    AGENT_ERROR,
    /** 被测系统调用异常 */
    TARGET_ERROR,
    /** 规则指标通过，但 judge 分数不达标 */
    JUDGE_FAIL
}
