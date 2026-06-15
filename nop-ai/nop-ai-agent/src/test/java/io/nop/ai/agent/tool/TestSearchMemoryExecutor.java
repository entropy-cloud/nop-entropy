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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 unit tests for {@link SearchMemoryExecutor}.
 *
 * <p>Covers matched results, the no-match legitimate success state, the
 * store-null honest error result, the missing-query fail-fast, and the
 * non-agent-context fail-fast.
 */
public class TestSearchMemoryExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private InMemoryAiMemoryStore store;
    private SearchMemoryExecutor executor;

    @BeforeEach
    void setUp() {
        store = new InMemoryAiMemoryStore();
        executor = new SearchMemoryExecutor();
    }

    private void seed(String key, String type, String content) {
        AiMemoryItem item = new AiMemoryItem();
        item.setKey(key);
        item.setType(type);
        item.setContent(content);
        item.setCreateTime(LocalDateTime.now());
        store.add(item);
    }

    private AgentToolExecuteContext contextWithStore() {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, null, "sess-search", "agent-search",
                null, null, null, store);
    }

    private AgentToolExecuteContext contextWithNullStore() {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, null, "sess-search", "agent-search",
                null, null, null, null);
    }

    private AiToolCall call(String input) {
        AiToolCall call = new AiToolCall();
        call.setToolName("search-memory");
        call.setId(1);
        call.setInput(input);
        return call;
    }

    @Test
    void returnsMatchingItems() throws Exception {
        seed("k1", "note", "User prefers concise answers");
        seed("k2", "note", "System uses English");
        seed("k3", "fact", "Unrelated note");

        AiToolCallResult result = executor.executeAsync(call("{\"query\":\"user\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("1 match(es)"));
        assertTrue(result.getOutput().getBody().contains("k1"));
    }

    @Test
    void noMatchesReturnsSuccessEmpty() throws Exception {
        seed("k1", "note", "alpha");

        AiToolCallResult result = executor.executeAsync(call("{\"query\":\"zzznomatch\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(), "no matches is a legitimate state, not an error");
        assertTrue(result.getOutput().getBody().contains("0 match(es)"));
        assertTrue(result.getOutput().getBody().contains("No matching memory items"));
    }

    @Test
    void matchesTypeAndKey() throws Exception {
        seed("foo-key", "fact", "unrelated");
        seed("k2", "NOT-match", "unrelated");

        AiToolCallResult byKey = executor.executeAsync(call("{\"query\":\"foo\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals("success", byKey.getStatus());
        assertTrue(byKey.getOutput().getBody().contains("foo-key"));

        AiToolCallResult byType = executor.executeAsync(call("{\"query\":\"fact\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals("success", byType.getStatus());
        assertTrue(byType.getOutput().getBody().contains("foo-key"));
    }

    @Test
    void missingQueryFailsFast() throws Exception {
        seed("k1", "note", "alpha");

        AiToolCallResult result = executor.executeAsync(call("{}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("'query' is required"));
    }

    @Test
    void emptyQueryFailsFast() throws Exception {
        AiToolCallResult result = executor.executeAsync(call("{\"query\":\"\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("'query' is required"));
    }

    @Test
    void nullStoreFailsFastHonestly() throws Exception {
        AiToolCallResult result = executor.executeAsync(call("{\"query\":\"anything\"}"), contextWithNullStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("no memory store available"),
                "Error must honestly report the missing store: " + result.getError().getBody());
    }

    @Test
    void nonAgentContextFailsFast() throws Exception {
        SimpleToolExecuteContext simpleCtx = new SimpleToolExecuteContext(new File("."), null, null);
        AiToolCallResult result = executor.executeAsync(call("{\"query\":\"anything\"}"), simpleCtx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("AgentToolExecuteContext"));
    }
}
