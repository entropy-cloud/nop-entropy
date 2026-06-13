package io.nop.ai.agent.router;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestPassThroughModelRouter {

    @Test
    void routeReturnsUnchangedOptionsWithoutTools() {
        ChatOptions options = new ChatOptions();
        options.setProvider("openai");
        options.setModel("gpt-4");

        List<ChatMessage> msgs = List.of(new ChatUserMessage("hello"));

        RoutingResult result = PassThroughModelRouter.passThrough().route(msgs, options, null);

        assertSame(options, result.getOptions());
        assertNull(result.getComplexity());
        assertEquals("pass-through", result.getRoutingReason());
    }

    @Test
    void routeReturnsUnchangedOptionsWithTools() {
        ChatOptions options = new ChatOptions();
        options.setModel("gpt-4");
        options.setTools(List.of(ChatToolDefinition.of("read_file", "Read a file")));
        options.autoToolChoice();

        List<ChatMessage> msgs = List.of(new ChatUserMessage("read test.txt"));

        RoutingResult result = PassThroughModelRouter.passThrough().route(msgs, options, null);

        assertSame(options, result.getOptions());
        assertEquals(1, result.getOptions().getTools().size());
        assertEquals("auto", result.getOptions().getToolChoice());
    }

    @Test
    void routeHandlesEmptyOptions() {
        ChatOptions options = new ChatOptions();

        List<ChatMessage> msgs = Collections.emptyList();

        RoutingResult result = PassThroughModelRouter.passThrough().route(msgs, options, null);

        assertSame(options, result.getOptions());
        assertNull(result.getOptions().getModel());
        assertNull(result.getOptions().getProvider());
    }

    @Test
    void routeWithFullOptionsPreservesAllFields() {
        ChatOptions options = new ChatOptions();
        options.setProvider("openai");
        options.setModel("gpt-4o");
        options.setTemperature(0.7f);
        options.setTopP(0.9f);
        options.setMaxTokens(4096);
        options.setTopK(50);

        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        List<ChatMessage> msgs = List.of(new ChatUserMessage("test"));

        RoutingResult result = PassThroughModelRouter.passThrough().route(msgs, options, ctx);

        ChatOptions returned = result.getOptions();
        assertSame(options, returned);
        assertEquals("openai", returned.getProvider());
        assertEquals("gpt-4o", returned.getModel());
        assertEquals(Float.valueOf(0.7f), returned.getTemperature());
        assertEquals(Float.valueOf(0.9f), returned.getTopP());
        assertEquals(Integer.valueOf(4096), returned.getMaxTokens());
        assertEquals(Integer.valueOf(50), returned.getTopK());
    }

    @Test
    void passThroughFactoryReturnsSingleton() {
        IModelRouter a = PassThroughModelRouter.passThrough();
        IModelRouter b = PassThroughModelRouter.passThrough();
        assertSame(a, b);
    }

    @Test
    void implementsIModelRouter() {
        assertSame(PassThroughModelRouter.passThrough(), PassThroughModelRouter.passThrough());
    }
}
