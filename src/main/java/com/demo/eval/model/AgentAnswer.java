package com.demo.eval.model;

import java.util.List;

/**
 * Agent /api/agent/chat 的返回结果。
 */
public record AgentAnswer(
        String answer,
        String status,
        List<ToolCall> toolCalls
) {
}
