package io.nop.ai.agent.router;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.List;

public final class PassThroughModelRouter implements IModelRouter {

    private PassThroughModelRouter() {
    }

    /**
     * Returns a fresh pass-through router instance. The class is stateless, so a new
     * instance per call is safe and avoids singleton identity coupling across tests
     * (MA5.6-AR-5).
     */
    public static IModelRouter passThrough() {
        return new PassThroughModelRouter();
    }

    @Override
    public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
        return new RoutingResult(options, null, "pass-through");
    }
}
