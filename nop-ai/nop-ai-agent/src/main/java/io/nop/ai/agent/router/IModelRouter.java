package io.nop.ai.agent.router;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.List;

public interface IModelRouter {

    RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx);
}
