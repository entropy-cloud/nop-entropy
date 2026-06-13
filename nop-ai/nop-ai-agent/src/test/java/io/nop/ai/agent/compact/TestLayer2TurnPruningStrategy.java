package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLayer2TurnPruningStrategy {

    private final Layer2TurnPruningStrategy strategy = new Layer2TurnPruningStrategy();

    private CompactionContext ctxWith(List<ChatMessage> messages, int triggerMaxMessages, double keepTailPercent) {
        AgentModel m = new AgentModel();
        m.setName("test-agent");
        CompactConfig config = new CompactConfig(0, null, true,
                CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS,
                CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                0.8, 0.9, keepTailPercent, triggerMaxMessages, "");
        AgentExecutionContext execCtx = new AgentExecutionContext(m);
        return new CompactionContext(messages, config, "s1", "agent1", execCtx, null);
    }

    private ChatAssistantMessage assistantWithToolCalls(String... ids) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        List<ChatToolCall> calls = new ArrayList<>();
        for (String id : ids) {
            ChatToolCall call = new ChatToolCall();
            call.setId(id);
            call.setName("bash");
            calls.add(call);
        }
        msg.setToolCalls(calls);
        return msg;
    }

    private ChatToolResponseMessage toolResponse(String toolCallId, String content) {
        return new ChatToolResponseMessage(toolCallId, "bash", content);
    }

    private List<ChatMessage> buildLongConversation(int turns) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system prompt"));
        messages.add(new ChatUserMessage("initial goal"));
        for (int i = 0; i < turns; i++) {
            String id = "tc-" + i;
            messages.add(assistantWithToolCalls(id));
            messages.add(toolResponse(id, "result-" + i + "-" + "X".repeat(40)));
        }
        return messages;
    }

    private Set<String> calledIds(List<ChatMessage> messages) {
        Set<String> ids = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatAssistantMessage) {
                ChatAssistantMessage asm = (ChatAssistantMessage) msg;
                if (asm.getToolCalls() != null) {
                    for (ChatToolCall tc : asm.getToolCalls()) {
                        if (tc.getId() != null) ids.add(tc.getId());
                    }
                }
            }
        }
        return ids;
    }

    private Set<String> respondedIds(List<ChatMessage> messages) {
        Set<String> ids = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatToolResponseMessage) {
                ids.add(((ChatToolResponseMessage) msg).getToolCallId());
            }
        }
        return ids;
    }

    @Test
    void pruningReducesMessageCount() {
        List<ChatMessage> messages = buildLongConversation(40);
        int original = messages.size();
        CompactionContext ctx = ctxWith(messages, 30, 0.15);

        CompactionResult result = strategy.compact(ctx);

        assertNotNull(result.getCompactedMessages());
        assertTrue(result.getCompactedMessages().size() < original,
                "Pruning should reduce message count: before=" + original + ", after=" + result.getCompactedMessages().size());
    }

    @Test
    void toolCallResponsePairingIntactAfterPruning() {
        List<ChatMessage> messages = buildLongConversation(40);
        CompactionContext ctx = ctxWith(messages, 30, 0.15);

        CompactionResult result = strategy.compact(ctx);
        List<ChatMessage> pruned = result.getCompactedMessages();

        Set<String> calls = calledIds(pruned);
        Set<String> responses = respondedIds(pruned);
        assertEquals(calls, responses,
                "Zero orphaned pairs: every tool_call must have a matching tool_response and vice versa");
    }

    @Test
    void headAnchorsPreserved() {
        List<ChatMessage> messages = buildLongConversation(40);
        CompactionContext ctx = ctxWith(messages, 30, 0.15);

        CompactionResult result = strategy.compact(ctx);
        List<ChatMessage> pruned = result.getCompactedMessages();

        assertFalse(pruned.isEmpty(), "Pruned list must not be empty");
        assertTrue(pruned.get(0) instanceof ChatSystemMessage, "First message must be system (head anchor)");

        boolean foundFirstUser = false;
        for (ChatMessage msg : pruned) {
            if (msg instanceof ChatUserMessage && "initial goal".equals(msg.getContent())) {
                foundFirstUser = true;
                break;
            }
        }
        assertTrue(foundFirstUser, "First user goal must be preserved as head anchor");
    }

    @Test
    void tailWindowPreserved() {
        List<ChatMessage> messages = buildLongConversation(40);
        int total = messages.size();
        CompactionContext ctx = ctxWith(messages, 30, 0.15);

        CompactionResult result = strategy.compact(ctx);
        List<ChatMessage> pruned = result.getCompactedMessages();

        ChatMessage lastOriginal = messages.get(total - 1);
        ChatMessage lastPruned = pruned.get(pruned.size() - 1);
        assertEquals(lastOriginal, lastPruned, "Last message (tail) must be preserved");

        ChatMessage secondLastOriginal = messages.get(total - 2);
        ChatMessage secondLastPruned = pruned.get(pruned.size() - 2);
        assertEquals(secondLastOriginal, secondLastPruned, "Second-to-last message (tail) must be preserved");
    }

    @Test
    void skipsExplicitlyWhenTooFewMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        messages.add(assistantWithToolCalls("tc-1"));
        messages.add(toolResponse("tc-1", "result"));

        CompactionContext ctx = ctxWith(messages, 30, 0.15);

        CompactionResult result = strategy.compact(ctx);

        assertNull(result.getCompactedMessages(), "Too-few-messages case returns explicit unchanged result");
        assertEquals(result.getTokensBefore(), result.getTokensAfter(),
                "No token change when skipping");
    }

    @Test
    void skipsExplicitlyWhenHeadTailOverlap() {
        List<ChatMessage> messages = buildLongConversation(31);
        CompactionContext ctx = ctxWith(messages, 5, 0.95);

        CompactionResult result = strategy.compact(ctx);

        assertNull(result.getCompactedMessages(),
                "When keepTailPercent is so high that head/tail overlap, skip explicitly");
        assertEquals(result.getTokensBefore(), result.getTokensAfter());
    }

    @Test
    void nameIsStable() {
        assertEquals("layer2-turn-pruning", strategy.name());
    }

    @Test
    void emptyMessagesHandledExplicitly() {
        CompactionContext ctx = ctxWith(Collections.emptyList(), 30, 0.15);
        CompactionResult result = strategy.compact(ctx);
        assertNull(result.getCompactedMessages());
        assertEquals(0, result.getTokensBefore());
    }

    @Test
    void wiringLayer2InvokedByPipelineAfterLayer1DidNotRelieve() {
        AtomicInteger microInvocations = new AtomicInteger(0);
        ICompressionStrategy microTracking = new ICompressionStrategy() {
            private final MicroCompressionCompactor delegate = new MicroCompressionCompactor();

            @Override
            public String name() {
                return "layer1-tracked";
            }

            @Override
            public CompactionResult compact(CompactionContext ctx) {
                microInvocations.incrementAndGet();
                return delegate.compact(ctx);
            }
        };

        AtomicInteger layer2Invocations = new AtomicInteger(0);
        Layer2TurnPruningStrategy trackedLayer2 = new Layer2TurnPruningStrategy() {
            @Override
            public CompactionResult compact(CompactionContext ctx) {
                layer2Invocations.incrementAndGet();
                return super.compact(ctx);
            }
        };

        PipelineCompactor pipeline = new PipelineCompactor(microTracking, trackedLayer2);

        List<ChatMessage> messages = buildLongConversation(40);
        CompactConfig config = new CompactConfig(0, null, true,
                CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS,
                CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                0.05, 0.9, 0.15, 5, "");
        AgentModel m = new AgentModel();
        m.setName("test-agent");
        AgentExecutionContext execCtx = new AgentExecutionContext(m);
        CompactionContext ctx = new CompactionContext(messages, config, "s1", "agent1", execCtx, null);

        CompactionResult result = pipeline.compact(ctx);

        assertTrue(microInvocations.get() >= 1, "Layer 1 must be invoked by pipeline");
        assertTrue(layer2Invocations.get() >= 1,
                "Layer 2 must be invoked by pipeline after Layer 1 did not relieve (message threshold exceeded)");
        assertNotNull(result.getCompactedMessages());

        Set<String> calls = calledIds(result.getCompactedMessages());
        Set<String> responses = respondedIds(result.getCompactedMessages());
        assertEquals(calls, responses, "Pairing intact through full pipeline");
    }
}
