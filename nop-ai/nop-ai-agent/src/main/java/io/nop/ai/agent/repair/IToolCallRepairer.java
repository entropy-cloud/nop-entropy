package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.messages.ChatToolCall;

public interface IToolCallRepairer {

    ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx);
}
