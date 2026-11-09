package com.demo.eval.model;

/**
 * Agent 的一次工具调用。
 */
public record ToolCall(
        String name,
        String arguments,
        String result
) {
}
