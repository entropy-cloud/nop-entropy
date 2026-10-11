package io.nop.ai.core.command;

import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.core.mock.InMemoryResponseProvider;
import io.nop.ai.core.mock.MockChatService;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.SimplePromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 focused tests for {@link AiCommand} on the {@code IChatService} stack:
 * the end-to-end execute path (prompt render -> ChatRequest -> IChatService ->
 * ChatExchange), the tool invocation loop over the {@link IAiToolInvoker} seam,
 * and the retry predicate — a success-but-empty response must keep retrying
 * (legacy {@code AiChatExchange::isValid} semantics, plan 2414 review A2).
 */
public class TestAiCommandExecute {

    private MockChatService mockService(Function<ChatRequest, ChatResponse> handler) {
        MockChatService service = new MockChatService();
        InMemoryResponseProvider provider = new InMemoryResponseProvider();
        provider.setResponseHandler(handler::apply);
        service.setResponseProvider(provider);
        return service;
    }

    private ChatResponse textResponse(String content) {
        return ChatResponse.success(new ChatAssistantMessage(content));
    }

    @Test
    public void testExecuteOnNewStackPath() {
        AtomicInteger calls = new AtomicInteger();
        MockChatService service = mockService(req -> {
            calls.incrementAndGet();
            assertNotNull(req.getOptions(), "options must be bound onto the ChatRequest");
            assertEquals("mock", req.getOptions().getProvider());
            return textResponse("section [NO_CHANGE]");
        });

        IPromptTemplate template = SimplePromptTemplate.simplePrompt("test/echo", "say {{word}}");
        AiCommand command = new AiCommand(service, null);
        command.promptTemplate(template);
        command.chatOptions(Map.of("provider", "mock", "model", "m1", "temperature", 0.5f));

        ChatExchange result = command.execute(Map.of("word", "hi"), null);

        assertTrue(result.isValid());
        assertEquals("section [NO_CHANGE]", result.getContent());
        assertEquals(1, calls.get(), "valid response must not trigger a retry");
    }

    @Test
    public void testSuccessButEmptyResponseRetries() {
        AtomicInteger calls = new AtomicInteger();
        MockChatService service = mockService(req -> {
            if (calls.incrementAndGet() == 1) {
                return textResponse(""); // transport success, empty content
            }
            return textResponse("recovered answer");
        });

        AiCommand command = new AiCommand(service, null);
        command.promptTemplate(SimplePromptTemplate.simplePrompt("test/retry", "ping"));
        command.retryTimesPerRequest(3);

        ChatExchange result = command.execute(Map.of(), null);

        assertTrue(result.isValid(), "second attempt must produce a valid result");
        assertEquals("recovered answer", result.getContent());
        assertEquals(2, calls.get(),
                "success-but-empty first response must trigger the retry (legacy isValid semantics)");
    }

    @Test
    public void testEmptyResponseExhaustingRetriesMarksInvalid() {
        AtomicInteger calls = new AtomicInteger();
        MockChatService service = mockService(req -> {
            calls.incrementAndGet();
            return textResponse("");
        });

        AiCommand command = new AiCommand(service, null);
        command.promptTemplate(SimplePromptTemplate.simplePrompt("test/empty", "ping"));
        command.retryTimesPerRequest(2);

        ChatExchange result = command.execute(Map.of(), null);

        assertEquals(3, calls.get(),
                "retryTimesPerRequest=2 means 2 retry hops after the first attempt (RetryHelper semantics)");
        assertTrue(result.isInvalid(), "empty final result must be marked invalid");
        assertEquals("nop.err.ai.service.result-is-empty", result.getInvalidReason().getErrorCode());
    }

    @Test
    public void testToolLoopInvokesSeamAndFeedsToolResponse() {
        AtomicInteger calls = new AtomicInteger();
        MockChatService service = mockService(req -> {
            if (calls.incrementAndGet() == 1) {
                return ChatResponse.success(List.of(
                        new ChatToolCallMessage("call-1", "echoTool", Map.of("arg", "value"))));
            }
            return textResponse("tool said: " + req.getLastNonSystemMessage().getContent());
        });

        AiCommand command = new AiCommand(service, null);
        command.promptTemplate(SimplePromptTemplate.simplePrompt("test/tool", "use tool"));
        command.maxSteps(3);
        AtomicInteger toolCalls = new AtomicInteger();
        command.toolInvoker(new IAiToolInvoker() {
            @Override
            public ChatToolDefinition findTool(String toolName) {
                assertEquals("echoTool", toolName);
                ChatToolDefinition definition = new ChatToolDefinition();
                definition.setName(toolName);
                definition.setDescription("echo test tool");
                return definition;
            }

            @Override
            public java.util.concurrent.CompletionStage<Object> callTool(String toolName, Map<String, Object> args) {
                toolCalls.incrementAndGet();
                assertEquals(Map.of("arg", "value"), args);
                return java.util.concurrent.CompletableFuture.completedFuture("42");
            }
        });
        command.setEnabledTools(Set.of("echoTool"));

        ChatExchange result = command.execute(Map.of(), null);

        assertTrue(result.isValid());
        assertEquals(1, toolCalls.get(), "the seam must be invoked exactly once");
        assertTrue(result.getContent().contains("tool said"), "final answer must follow the tool response");
    }
}
