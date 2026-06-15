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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 unit tests for {@link ReadMemoryExecutor}.
 *
 * <p>Covers all four {@code action} modes (list / last / budgeted / key),
 * type-filtering, the empty-store legitimate state, the store-null honest
 * error result, and the non-agent-context fail-fast.
 */
public class TestReadMemoryExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private InMemoryAiMemoryStore store;
    private ReadMemoryExecutor executor;

    @BeforeEach
    void setUp() {
        store = new InMemoryAiMemoryStore();
        executor = new ReadMemoryExecutor();
    }

    private AiMemoryItem item(String key, String type, String content, int priority, boolean pinned) {
        AiMemoryItem item = new AiMemoryItem();
        item.setKey(key);
        item.setType(type);
        item.setContent(content);
        item.setPriority(priority);
        item.setPinned(pinned);
        item.setCreateTime(LocalDateTime.now());
        return item;
    }

    private AgentToolExecuteContext contextWithStore() {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, null, "sess-read", "agent-read",
                null, null, null, store);
    }

    private AgentToolExecuteContext contextWithNullStore() {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, null, "sess-read", "agent-read",
                null, null, null, null);
    }

    private AiToolCall call(String input) {
        AiToolCall call = new AiToolCall();
        call.setToolName("read-memory");
        call.setId(1);
        call.setInput(input);
        return call;
    }

    @Test
    void listReturnsAllItems() throws Exception {
        store.add(item("k1", "note", "alpha", 0, false));
        store.add(item("k2", "fact", "beta", 0, false));

        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"list\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("2 item(s)"));
        assertTrue(result.getOutput().getBody().contains("k1"));
        assertTrue(result.getOutput().getBody().contains("k2"));
    }

    @Test
    void listWithTypeFilter() throws Exception {
        store.add(item("k1", "note", "alpha", 0, false));
        store.add(item("k2", "fact", "beta", 0, false));

        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"list\",\"type\":\"note\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("1 item(s)"));
        assertTrue(result.getOutput().getBody().contains("k1"));
        assertFalse(result.getOutput().getBody().contains("k2"));
    }

    @Test
    void listEmptyStoreReturnsSuccessEmpty() throws Exception {
        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"list\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("0 item(s)"));
        assertTrue(result.getOutput().getBody().contains("no memory items"));
    }

    @Test
    void lastReturnsMostRecentN() throws Exception {
        store.add(item("k1", "note", "first", 0, false));
        store.add(item("k2", "note", "second", 0, false));
        store.add(item("k3", "note", "third", 0, false));

        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"last\",\"n\":2}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("2 item(s)"));
        assertTrue(result.getOutput().getBody().contains("k2"));
        assertTrue(result.getOutput().getBody().contains("k3"));
    }

    @Test
    void lastWithoutNFailsFast() throws Exception {
        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"last\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("requires a positive 'n'"));
    }

    @Test
    void budgetedReturnsSelectedItems() throws Exception {
        store.add(item("low", "note", "abcd1234", 1, false));
        store.add(item("high", "note", "abcd1234", 5, false));
        store.add(item("pinned1", "note", "abcd1234", 0, true));

        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"budgeted\",\"maxTokens\":4}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        String body = result.getOutput().getBody();
        assertTrue(body.contains("pinned1"));
        assertTrue(body.contains("high"));
        assertFalse(body.contains("\nlow\n") && body.contains("low"),
                "low should not be in budgeted result: " + body);
    }

    @Test
    void budgetedWithoutMaxTokensFailsFast() throws Exception {
        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"budgeted\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("requires a positive 'maxTokens'"));
    }

    @Test
    void keyReturnsSingleItem() throws Exception {
        store.add(item("k1", "note", "alpha", 0, false));

        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"key\",\"key\":\"k1\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("k1"));
        assertTrue(result.getOutput().getBody().contains("alpha"));
    }

    @Test
    void keyNotFoundReturnsSuccess() throws Exception {
        store.add(item("k1", "note", "alpha", 0, false));

        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"key\",\"key\":\"missing\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("No memory item found"));
    }

    @Test
    void unknownActionFailsFast() throws Exception {
        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"invalid\"}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("unknown action"));
    }

    @Test
    void nullStoreFailsFastHonestly() throws Exception {
        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"list\"}"), contextWithNullStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("no memory store available"),
                "Error must honestly report the missing store: " + result.getError().getBody());
    }

    @Test
    void nonAgentContextFailsFast() throws Exception {
        SimpleToolExecuteContext simpleCtx = new SimpleToolExecuteContext(new File("."), null, null);
        AiToolCallResult result = executor.executeAsync(call("{\"action\":\"list\"}"), simpleCtx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("AgentToolExecuteContext"));
    }

    @Test
    void defaultsActionToList() throws Exception {
        store.add(item("k1", "note", "alpha", 0, false));

        AiToolCallResult result = executor.executeAsync(call("{}"), contextWithStore())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("1 item(s)"));
    }
}
