package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.messages.ChatToolCall;

public final class NoOpToolCallRepairer implements IToolCallRepairer {

    public static final NoOpToolCallRepairer INSTANCE = new NoOpToolCallRepairer();

    private NoOpToolCallRepairer() {
    }

    @Override
    public ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx) {
        return toolCall;
    }
}
