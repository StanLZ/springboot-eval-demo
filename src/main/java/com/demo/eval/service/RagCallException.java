package com.demo.eval.service;

/**
 * 调用被测 RAG 服务失败时抛出，message 会直接展示到评测报告的 error 字段。
 */
public class RagCallException extends RuntimeException {

    public RagCallException(String message) {
        super(message);
    }

    public RagCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
