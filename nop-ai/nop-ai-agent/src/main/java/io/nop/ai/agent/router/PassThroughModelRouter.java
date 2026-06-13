package io.nop.ai.agent.router;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.List;

public final class PassThroughModelRouter implements IModelRouter {

    private static final PassThroughModelRouter INSTANCE = new PassThroughModelRouter();

    private PassThroughModelRouter() {
    }

    public static IModelRouter passThrough() {
        return INSTANCE;
    }

    @Override
    public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
        return new RoutingResult(options, null, "pass-through");
    }
}
