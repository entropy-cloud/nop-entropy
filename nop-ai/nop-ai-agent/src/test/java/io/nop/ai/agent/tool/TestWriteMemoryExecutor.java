package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.SimpleToolExecuteContext;
import io.nop.ai.agent.memory.InMemoryAiMemoryStore;
import io.nop.ai.agent.memory.AiMemoryItem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 unit tests for {@link WriteMemoryExecutor}.
 *
 * <p>Covers all four {@code action} modes (add / update / remove / clear),
 * state verification after each operation, the store-null honest error result,
 * missing-required-argument fail-fast, and the non-agent-context fail-fast.
 */
public class TestWriteMemoryExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private InMemoryAiMemoryStore store;
    private WriteMemoryExecutor executor;

    @BeforeEach
    void setUp() {
        store = new InMemoryAiMemoryStore();
        executor = new WriteMemoryExecutor();
    }

    private AgentToolExecuteContext contextWithStore() {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, null, "sess-write", "agent-write",
                null, null, null, store);
    }

    private AgentToolExecuteContext contextWithNullStore() {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, null, "sess-write", "agent-write",
                null, null, null, null);
    }

    private AiToolCall call(String input) {
        AiToolCall call = new AiToolCall();
        call.setToolName("write-memory");
        call.setId(1);
        call.setInput(input);
        return call;
    }

    @Test
    void addInsertsItem() throws Exception {
        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"add\",\"key\":\"k1\",\"type\":\"note\",\"content\":\"hello\",\"priority\":3,\"pinned\":true}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Added memory item"));
        assertTrue(result.getOutput().getBody().contains("key=k1"));
        assertEquals(1, store.size());

        AiMemoryItem stored = store.findByKey("k1");
        assertNotNull(stored);
        assertEquals("hello", stored.getContent());
        assertEquals("note", stored.getType());
        assertEquals(3, stored.getPriority());
        assertTrue(stored.isPinned());
    }

    @Test
    void addWithoutKeyAutoGenerates() throws Exception {
        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"add\",\"content\":\"no key\"}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertEquals(1, store.size());
    }

    @Test
    void addWithoutContentFailsFast() throws Exception {
        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"add\",\"key\":\"k1\"}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("content"));
        assertEquals(0, store.size());
    }

    @Test
    void updateOverwritesByKey() throws Exception {
        AiMemoryItem original = new AiMemoryItem();
        original.setKey("k1");
        original.setContent("old");
        store.add(original);

        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"update\",\"key\":\"k1\",\"content\":\"new\",\"priority\":5}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Updated memory item"));
        assertEquals(1, store.size());

        AiMemoryItem stored = store.findByKey("k1");
        assertEquals("new", stored.getContent());
        assertEquals(5, stored.getPriority());
    }

    @Test
    void updateWithoutKeyFailsFast() throws Exception {
        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"update\",\"content\":\"x\"}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("key"));
    }

    @Test
    void removeDeletesByKey() throws Exception {
        AiMemoryItem item = new AiMemoryItem();
        item.setKey("k1");
        item.setContent("v1");
        store.add(item);
        AiMemoryItem item2 = new AiMemoryItem();
        item2.setKey("k2");
        item2.setContent("v2");
        store.add(item2);

        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"remove\",\"key\":\"k1\"}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Removed memory item"));
        assertTrue(result.getOutput().getBody().contains("Remaining items in store: 1"));
        assertEquals(1, store.size());
        assertNull(store.findByKey("k1"));
        assertNotNull(store.findByKey("k2"));
    }

    @Test
    void removeWithoutKeyFailsFast() throws Exception {
        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"remove\"}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("key"));
    }

    @Test
    void clearRemovesAllItems() throws Exception {
        AiMemoryItem item1 = new AiMemoryItem();
        item1.setKey("k1");
        item1.setContent("v1");
        store.add(item1);
        AiMemoryItem item2 = new AiMemoryItem();
        item2.setKey("k2");
        item2.setContent("v2");
        store.add(item2);

        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"clear\"}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Cleared all memory items"));
        assertEquals(0, store.size());
    }

    @Test
    void unknownActionFailsFast() throws Exception {
        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"invalid\"}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("unknown action"));
    }

    @Test
    void nullStoreFailsFastHonestly() throws Exception {
        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"add\",\"key\":\"k1\",\"content\":\"x\"}"),
                contextWithNullStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("no memory store available"),
                "Error must honestly report the missing store: " + result.getError().getBody());
    }

    @Test
    void nonAgentContextFailsFast() throws Exception {
        SimpleToolExecuteContext simpleCtx = new SimpleToolExecuteContext(new File("."), null, null);
        AiToolCallResult result = executor.executeAsync(call(
                "{\"action\":\"add\",\"key\":\"k1\",\"content\":\"x\"}"), simpleCtx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("AgentToolExecuteContext"));
    }

    @Test
    void defaultsActionToAdd() throws Exception {
        AiToolCallResult result = executor.executeAsync(call(
                "{\"key\":\"k1\",\"content\":\"hello\"}"),
                contextWithStore()).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertEquals(1, store.size());
        assertEquals("hello", store.findByKey("k1").getContent());
    }
}
