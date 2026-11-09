package com.demo.eval.model;

/**
 * 单条用例的失败类型，用于报告里的失败归因。
 */
public enum FailureType {
    PASS,
    RETRIEVAL_MISS,
    ANSWER_INCOMPLETE,
    TARGET_ERROR
}
