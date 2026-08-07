package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.agent.support.ChatResponseFixtures;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.ICompactionArchive;
import io.nop.ai.toolkit.compact.ShortRef;
import io.nop.ai.toolkit.compact.ShortRefHasher;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReferenceCompactionStrategy} covering the three required
 * paths (design §8.2): has referenceable content (produces shortRef + archives
 * original), no referenceable content (explicit unchanged), and hash-addressed
 * read-back (archive stores by content hash). Plus pipeline wiring (the
 * strategy is invoked by PipelineCompactor in escalation order).
 */
public class TestReferenceCompactionStrategy {

    // The default token estimator (CalibratedTokenEstimator over the openai
    // dialect) needs the nop resource registry initialised, so the wiring
    // test (which exercises real token estimation through PipelineCompactor)
    // requires CoreInitialization. Direct strategy tests would also work
    // without it, but initialising once keeps the suite uniform.
    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final int THRESHOLD = 100;

    private final ReferenceCompactionStrategy strategy = new ReferenceCompactionStrategy(THRESHOLD);

    private AgentModel agentModel() {
        AgentModel m = new AgentModel();
        m.setName("test-agent");
        return m;
    }

    private CompactionContext ctxWith(List<ChatMessage> messages, ICompactionArchive archive) {
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        return new CompactionContext(messages, CompactConfig.defaults(), "s1", "agent1",
                execCtx, null, archive);
    }

    private void assistantWithToolCalls(List<ChatMessage> messages, String... toolCallIds) {
        ChatToolCall[] calls = new ChatToolCall[toolCallIds.length];
        for (int i = 0; i < toolCallIds.length; i++) {
            calls[i] = new ChatToolCall();
            calls[i].setId(toolCallIds[i]);
            calls[i].setName("read-file");
        }
        messages.addAll(ChatResponseFixtures.foldedAssistantWithToolCalls(null, calls));
    }

    private ChatToolResponseMessage fileToolResponse(String toolCallId, String content) {
        return new ChatToolResponseMessage(toolCallId, "read-file", content);
    }

    private String longContent(int chars) {
        StringBuilder sb = new StringBuilder(chars);
        while (sb.length() < chars) {
            sb.append("x");
        }
        return sb.toString();
    }

    // ---- Path 1: has referenceable content ----

    @Test
    void hasReferenceableContentProducesShortRefAndArchivesOriginal() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        String original = longContent(THRESHOLD + 50);

        // Use CompactConfig with maxRecentToolResults=0 so all tool responses
        // are candidates regardless of recency.
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        assistantWithToolCalls(messages, "tc-1");
        messages.add(fileToolResponse("tc-1", original));

        CompactConfig config = new CompactConfig(0, null, true, 0, 8000);
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        CompactionContext ctx = new CompactionContext(messages, config, "s1", "agent1", execCtx, null, archive);

        CompactionResult result = strategy.compact(ctx);

        assertNotNull(result.getCompactedMessages(),
                "reference compaction must produce compacted messages when referenceable content exists");
        assertTrue(result.getTokensAfter() < result.getTokensBefore(),
                "tokens must decrease after replacing long content with a short pointer");

        // The tool response content is now a shortRef pointer (Plan 329：按类型查找，避免 ChatToolCallMessage 偏移下标)
        ChatToolResponseMessage archived = result.getCompactedMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst().orElse(null);
        assertNotNull(archived);
        String newContent = archived.getContent();
        assertTrue(newContent.contains(ShortRef.MARKER),
                "content should be replaced with a shortRef pointer: " + newContent);

        // The shortRef carries the hash, and the original is archived under it
        ShortRef ref = ShortRef.parseFirst(newContent);
        assertNotNull(ref, "shortRef must be parseable from the new content");
        String hash = ref.getHash();
        assertEquals(ShortRefHasher.hash(original), hash,
                "shortRef hash must equal the content's hash");
        assertEquals(ShortRef.TYPE_FILE, ref.getType(),
                "type should map from the producing tool (read-file -> file)");
        assertTrue(archive.contains(hash), "archive must contain the original under the hash");
        assertEquals(original, archive.getByHash(hash),
                "archive must hold the exact original content, read-back by hash");
    }

    // ---- Path 2: no referenceable content -> explicit unchanged ----

    @Test
    void noArchiveWiredReturnsExplicitUnchanged() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        // archive == null
        CompactionContext ctx = ctxWith(messages, null);

        CompactionResult result = strategy.compact(ctx);

        assertEquals(result.getTokensBefore(), result.getTokensAfter(),
                "no archive -> explicit unchanged (tokensAfter==tokensBefore)");
        assertNull(result.getCompactedMessages(),
                "no archive -> compactedMessages null (PipelineCompactor treats as skip-layer)");
    }

    @Test
    void noReferenceableToolResponsesReturnsExplicitUnchanged() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        messages.add(new ChatUserMessage("a conversation message, no tool response here"));

        CompactionContext ctx = ctxWith(messages, archive);

        CompactionResult result = strategy.compact(ctx);

        assertEquals(result.getTokensBefore(), result.getTokensAfter(),
                "no tool responses -> explicit unchanged");
        assertNull(result.getCompactedMessages());
        assertEquals(0, ((InSessionCompactionArchive) archive).size(),
                "archive should have nothing put when no referenceable content");
    }

    @Test
    void shortToolResponseNotReferencedStaysInline() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        String shortContent = "short"; // well below threshold

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        assistantWithToolCalls(messages, "tc-1");
        messages.add(fileToolResponse("tc-1", shortContent));

        // force older via maxRecent=0
        CompactConfig config = new CompactConfig(0, null, true, 0, 8000);
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        CompactionContext ctx = new CompactionContext(messages, config, "s1", "agent1", execCtx, null, archive);

        CompactionResult result = strategy.compact(ctx);

        assertNull(result.getCompactedMessages(),
                "short tool response below threshold -> explicit unchanged (kept inline)");
        assertEquals(0, ((InSessionCompactionArchive) archive).size());
    }

    @Test
    void nonReferenceableToolNotReferenced() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        // bash is not in ShortRef.typeForTool map -> not referenceable

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        messages.add(new ChatToolResponseMessage("tc-1", "bash", longContent(THRESHOLD + 50)));

        CompactConfig config = new CompactConfig(0, null, true, 0, 8000);
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        CompactionContext ctx = new CompactionContext(messages, config, "s1", "agent1", execCtx, null, archive);

        CompactionResult result = strategy.compact(ctx);

        assertNull(result.getCompactedMessages(),
                "non-referenceable tool (bash) -> explicit unchanged, content stays inline");
        assertEquals(0, ((InSessionCompactionArchive) archive).size());
    }

    @Test
    void recentToolResponseKeptVerbatim() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        String original = longContent(THRESHOLD + 50);
        // default maxRecentToolResults=6 -> with only 1 tool response, it IS recent
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        assistantWithToolCalls(messages, "tc-1");
        messages.add(fileToolResponse("tc-1", original));

        CompactionContext ctx = ctxWith(messages, archive); // defaults: maxRecent=6

        CompactionResult result = strategy.compact(ctx);

        assertNull(result.getCompactedMessages(),
                "recent tool response (within maxRecent window) must be kept verbatim");
        assertEquals(0, ((InSessionCompactionArchive) archive).size(),
                "nothing archived when the only tool response is in the recent window");
    }

    @Test
    void emptyMessageListReturnsExplicitZeroResult() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        CompactionContext ctx = ctxWith(Collections.emptyList(), archive);

        CompactionResult result = strategy.compact(ctx);

        assertEquals(0, result.getTokensBefore());
        assertEquals(0, result.getTokensAfter());
        assertEquals(0, result.getRetainedMessageCount());
        assertNull(result.getCompactedMessages());
    }

    // ---- Path 3: hash-addressed read-back (archive stores by content hash) ----

    @Test
    void multipleDistinctLongContentsGetDistinctHashes() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        String contentA = longContent(THRESHOLD + 10).replace('x', 'A');
        String contentB = longContent(THRESHOLD + 20).replace('x', 'B');

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        assistantWithToolCalls(messages, "tc-1", "tc-2");
        messages.add(fileToolResponse("tc-1", contentA));
        messages.add(fileToolResponse("tc-2", contentB));

        CompactConfig config = new CompactConfig(0, null, true, 0, 8000);
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        CompactionContext ctx = new CompactionContext(messages, config, "s1", "agent1", execCtx, null, archive);

        CompactionResult result = strategy.compact(ctx);

        assertNotNull(result.getCompactedMessages());
        // Both archived under distinct hashes, both read back exactly
        assertEquals(2, ((InSessionCompactionArchive) archive).size());
        assertEquals(contentA, archive.getByHash(ShortRefHasher.hash(contentA)));
        assertEquals(contentB, archive.getByHash(ShortRefHasher.hash(contentB)));
    }

    // ---- Wiring: strategy invoked by PipelineCompactor in escalation order ----

    @Test
    void wiringStrategyInvokedByPipelineAndOutputConsumed() {
        final AtomicInteger invocations = new AtomicInteger(0);
        ICompactionArchive archive = new InSessionCompactionArchive();

        // Wrap the real strategy to count invocations
        ReferenceCompactionStrategy realStrategy = new ReferenceCompactionStrategy(THRESHOLD);
        ICompressionStrategy counting = new ICompressionStrategy() {
            @Override
            public String name() {
                return realStrategy.name();
            }

            @Override
            public CompactionResult compact(CompactionContext ctx) {
                invocations.incrementAndGet();
                return realStrategy.compact(ctx);
            }
        };

        PipelineCompactor pipeline = new PipelineCompactor(counting);

        String original = longContent(THRESHOLD + 50);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        assistantWithToolCalls(messages, "tc-1");
        messages.add(fileToolResponse("tc-1", original));

        // Use a config with a low token threshold so the pipeline does NOT
        // consider itself relieved after reference compaction (we want to
        // verify the strategy runs even when escalation continues).
        CompactConfig config = new CompactConfig(0, null, true, 0, 8000,
                0.0, 0.9, 0.15, 30, "");
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        CompactionContext ctx = new CompactionContext(messages, config, "s1", "agent1", execCtx, null, archive);

        CompactionResult result = pipeline.compact(ctx);

        assertEquals(1, invocations.get(),
                "Wiring: PipelineCompactor must invoke the reference strategy at runtime");
        assertNotNull(result.getCompactedMessages(),
                "Wiring: pipeline must consume the strategy's output (compacted messages present)");
        assertTrue(result.getTokensAfter() < result.getTokensBefore());
    }

    @Test
    void nameIsStable() {
        assertEquals(ReferenceCompactionStrategy.NAME, strategy.name());
        assertEquals("reference-compaction", strategy.name());
    }
}
