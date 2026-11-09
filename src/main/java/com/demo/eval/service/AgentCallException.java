package com.demo.eval.service;

/**
 * 调用被测 Agent 失败时抛出，message 会写入评测结果的 error 字段。
 */
public class AgentCallException extends RuntimeException {

    public AgentCallException(String message) {
        super(message);
    }

    public AgentCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
