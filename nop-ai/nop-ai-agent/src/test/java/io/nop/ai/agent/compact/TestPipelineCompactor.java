package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.ChatOptionsModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPipelineCompactor {

    private static final int MAX_CONTEXT_TOKENS = 1000;

    private AgentModel agentModel() {
        AgentModel m = new AgentModel();
        m.setName("test-agent");
        ChatOptionsModel opts = new ChatOptionsModel();
        opts.setMaxTokens(MAX_CONTEXT_TOKENS);
        m.setChatOptions(opts);
        return m;
    }

    private CompactConfig config(double triggerTokenPercent, int triggerMaxMessages) {
        return new CompactConfig(0, null, true,
                CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS,
                CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                triggerTokenPercent, CompactConfig.DEFAULT_FORCED_STOP_PERCENT,
                CompactConfig.DEFAULT_KEEP_TAIL_PERCENT, triggerMaxMessages,
                CompactConfig.DEFAULT_COMPRESSION_MODEL);
    }

    private CompactionContext ctxWith(List<ChatMessage> messages, CompactConfig config) {
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        return new CompactionContext(messages, config, "s1", "agent1", execCtx, null);
    }

    private List<ChatMessage> bigMessages(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        for (int i = 0; i < count; i++) {
            messages.add(new ChatUserMessage("message-padding-content-" + i + "-".repeat(50)));
        }
        return messages;
    }

    /**
     * A strategy that always reports a fixed token reduction (relieves the context)
     * so we can control escalation. Records invocation count.
     */
    static final class CountingRelievingStrategy implements ICompressionStrategy {
        final String name;
        final AtomicInteger invocations = new AtomicInteger(0);
        final long fakeTokensAfter;

        CountingRelievingStrategy(String name, long fakeTokensAfter) {
            this.name = name;
            this.fakeTokensAfter = fakeTokensAfter;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompactionResult compact(CompactionContext ctx) {
            invocations.incrementAndGet();
            List<ChatMessage> reduced = new ArrayList<>(ctx.getMessages().subList(0, Math.min(2, ctx.getMessages().size())));
            long tokensBefore = NoOpContextCompactor.resolveEstimator(ctx).estimateTokens(ctx.getMessages());
            return new CompactionResult(ctx.getSessionId(), tokensBefore, fakeTokensAfter,
                    reduced.size(), null, reduced);
        }
    }

    /**
     * A strategy that does NOT relieve (returns unchanged result). Used to force
     * escalation to the next layer.
     */
    static final class CountingNoOpStrategy implements ICompressionStrategy {
        final String name;
        final AtomicInteger invocations = new AtomicInteger(0);

        CountingNoOpStrategy(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompactionResult compact(CompactionContext ctx) {
            invocations.incrementAndGet();
            long tokens = NoOpContextCompactor.resolveEstimator(ctx).estimateTokens(ctx.getMessages());
            return new CompactionResult(ctx.getSessionId(), tokens, tokens, ctx.getMessages().size(), null, null);
        }
    }

    @Test
    void orchestratorEscalatesOnlyWhenPreviousLayerDidNotRelieve() {
        CountingNoOpStrategy layer1 = new CountingNoOpStrategy("layer1");
        CountingRelievingStrategy layer2 = new CountingRelievingStrategy("layer2", 1);

        PipelineCompactor pipeline = new PipelineCompactor(layer1, layer2);

        List<ChatMessage> messages = bigMessages(40);
        CompactionContext ctx = ctxWith(messages, config(0.05, 30));

        CompactionResult result = pipeline.compact(ctx);

        assertEquals(1, layer1.invocations.get(), "Layer 1 should be invoked");
        assertEquals(1, layer2.invocations.get(), "Layer 2 should be invoked after layer 1 did not relieve");
        assertNotNull(result.getCompactedMessages());
        assertTrue(result.getTokensAfter() < result.getTokensBefore(),
                "Final result should reflect layer 2 reduction");
    }

    @Test
    void orchestratorStopsEscalationWhenLayerRelieves() {
        CountingRelievingStrategy layer1 = new CountingRelievingStrategy("layer1", 1);
        CountingNoOpStrategy layer2 = new CountingNoOpStrategy("layer2");
        CountingNoOpStrategy layer3 = new CountingNoOpStrategy("layer3");

        PipelineCompactor pipeline = new PipelineCompactor(layer1, layer2, layer3);

        List<ChatMessage> messages = bigMessages(40);
        CompactionContext ctx = ctxWith(messages, config(0.05, 30));

        CompactionResult result = pipeline.compact(ctx);

        assertEquals(1, layer1.invocations.get(), "Layer 1 should be invoked");
        assertEquals(0, layer2.invocations.get(), "Layer 2 should NOT be invoked because layer 1 relieved");
        assertEquals(0, layer3.invocations.get(), "Layer 3 should NOT be invoked because layer 1 relieved");
        assertNotNull(result.getCompactedMessages());
    }

    @Test
    void respectsCompactConfigThresholds() {
        CountingRelievingStrategy layer1 = new CountingRelievingStrategy("layer1", 1);

        PipelineCompactor pipeline = new PipelineCompactor(layer1);

        List<ChatMessage> messages = bigMessages(40);
        CompactionContext ctx = ctxWith(messages, config(0.05, 30));

        CompactionResult result = pipeline.compact(ctx);

        assertEquals(1, layer1.invocations.get(), "Layer 1 invoked because above threshold");
        assertTrue(result.getTokensAfter() < result.getTokensBefore());
    }

    @Test
    void belowThresholdDoesNotInvokeAnyLayer() {
        CountingNoOpStrategy layer1 = new CountingNoOpStrategy("layer1");

        PipelineCompactor pipeline = new PipelineCompactor(layer1);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("s"));
        messages.add(new ChatUserMessage("hi"));
        CompactionContext ctx = ctxWith(messages, config(0.8, 30));

        CompactionResult result = pipeline.compact(ctx);

        assertEquals(0, layer1.invocations.get(), "Layer 1 should NOT be invoked when below thresholds");
        assertNull(result.getCompactedMessages(), "No compaction when below thresholds");
    }

    @Test
    void singleStrategyPipelineBackwardCompatible() {
        MicroCompressionCompactor micro = new MicroCompressionCompactor();
        PipelineCompactor pipeline = new PipelineCompactor(micro);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));

        List<ChatMessage> toolMsgs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            io.nop.ai.api.chat.messages.ChatAssistantMessage asm = new io.nop.ai.api.chat.messages.ChatAssistantMessage();
            io.nop.ai.api.chat.messages.ChatToolCall call = new io.nop.ai.api.chat.messages.ChatToolCall();
            call.setId("tc-" + i);
            call.setName("bash");
            asm.setToolCalls(Collections.singletonList(call));
            messages.add(asm);
            io.nop.ai.api.chat.messages.ChatToolResponseMessage resp =
                    new io.nop.ai.api.chat.messages.ChatToolResponseMessage("tc-" + i, "bash", "X".repeat(5000));
            messages.add(resp);
            toolMsgs.add(resp);
        }

        CompactionContext ctx = ctxWith(messages, config(0.05, 5));

        CompactionResult result = pipeline.compact(ctx);

        assertNotNull(result.getCompactedMessages());
        assertTrue(result.getTokensAfter() < result.getTokensBefore(),
                "Single-strategy pipeline (Layer 1 micro) should reduce tokens");
    }

    @Test
    void emptyStrategiesReturnsExplicitNoOpResult() {
        PipelineCompactor pipeline = new PipelineCompactor(Collections.emptyList());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("hello"));
        CompactionContext ctx = ctxWith(messages, config(0.05, 30));

        CompactionResult result = pipeline.compact(ctx);

        assertNotNull(result, "Empty-strategy pipeline must return explicit result, not null");
        assertEquals(result.getTokensBefore(), result.getTokensAfter(),
                "Empty-strategy pipeline returns NoOp-equivalent (no reduction)");
    }

    @Test
    void emptyMessageListHandledExplicitly() {
        PipelineCompactor pipeline = new PipelineCompactor(new MicroCompressionCompactor());

        CompactionContext ctx = ctxWith(Collections.emptyList(), config(0.05, 30));

        CompactionResult result = pipeline.compact(ctx);

        assertNotNull(result);
        assertEquals(0, result.getTokensBefore());
        assertEquals(0, result.getTokensAfter());
        assertEquals(0, result.getRetainedMessageCount());
        assertNull(result.getCompactedMessages());
    }

    @Test
    void wiringVerificationOrchestratorInvokesComposedStrategy() {
        AtomicInteger invoked = new AtomicInteger(0);
        AtomicReference<List<ChatMessage>> captured = new AtomicReference<>();

        ICompressionStrategy tracking = new ICompressionStrategy() {
            @Override
            public String name() {
                return "tracking";
            }

            @Override
            public CompactionResult compact(CompactionContext ctx) {
                invoked.incrementAndGet();
                captured.set(ctx.getMessages());
                long tokens = NoOpContextCompactor.resolveEstimator(ctx).estimateTokens(ctx.getMessages());
                List<ChatMessage> reduced = new ArrayList<>(ctx.getMessages().subList(0, 2));
                long after = NoOpContextCompactor.resolveEstimator(ctx).estimateTokens(reduced);
                return new CompactionResult(ctx.getSessionId(), tokens, after, reduced.size(), null, reduced);
            }
        };

        PipelineCompactor pipeline = new PipelineCompactor(tracking);
        List<ChatMessage> messages = bigMessages(40);
        CompactionContext ctx = ctxWith(messages, config(0.05, 30));

        CompactionResult result = pipeline.compact(ctx);

        assertEquals(1, invoked.get(), "Wiring: orchestrator must actually invoke composed strategy's compact at runtime");
        assertNotNull(captured.get(), "Wiring: orchestrator must pass messages to the composed strategy");
        assertNotNull(result.getCompactedMessages());
        assertTrue(result.getTokensAfter() < result.getTokensBefore());
    }

    @Test
    void iCompressionStrategyInterfaceContract() {
        ICompressionStrategy micro = new MicroCompressionCompactor();
        assertEquals("layer1-micro-compression", micro.name());
    }

    @Test
    void strategiesListExposed() {
        ICompressionStrategy s1 = new CountingNoOpStrategy("a");
        ICompressionStrategy s2 = new CountingNoOpStrategy("b");
        PipelineCompactor pipeline = new PipelineCompactor(Arrays.asList(s1, s2));
        assertEquals(2, pipeline.getStrategies().size());
    }
}
