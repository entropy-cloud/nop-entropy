package io.nop.ai.agent.router;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestIModelRouter {

    @Test
    void interfaceContractCanBeImplemented() {
        IModelRouter router = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                return new RoutingResult(options, null, "test-impl");
            }
        };

        ChatOptions opts = new ChatOptions();
        opts.setModel("test");
        List<ChatMessage> msgs = Collections.singletonList(new ChatUserMessage("hello"));

        RoutingResult result = router.route(msgs, opts, null);

        assertTrue(result instanceof RoutingResult);
    }

    @Test
    void interfaceIsAssignableFromPassThrough() {
        assertTrue(IModelRouter.class.isAssignableFrom(PassThroughModelRouter.class));
    }
}
