package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 296 (WS2): unit tests for tag-based tool visibility filtering in
 * {@link ReActAgentExecutor#buildToolDefinitions}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Empty activeTags → all declared tools visible</li>
 *   <li>activeTags intersection filtering</li>
 *   <li>denyTags removes tools containing any denied tag</li>
 *   <li>denyTools removes tools by name (priority over tags)</li>
 *   <li>meta tools are always visible regardless of activeTags/denyTags</li>
 *   <li>Runtime session override (set-active-tags) is reflected in next filtering</li>
 *   <li>_tools whitelist still works (backward compat)</li>
 * </ul>
 */
public class TestToolVisibilityFiltering {

    /**
     * A minimal in-memory IToolManager that returns a fixed list of tools.
     */
    static class StubToolManager implements IToolManager {
        final List<AiToolModel> tools;

        StubToolManager(List<AiToolModel> tools) {
            this.tools = tools;
        }

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
            return CompletableFuture.completedFuture(new AiToolCallResult());
        }

        @Override
        public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
            return CompletableFuture.completedFuture(new AiToolCallsResponse());
        }

        @Override
        public List<AiToolModel> listTools() {
            return tools;
        }

        @Override
        public AiToolModel loadTool(String toolName) {
            return tools.stream().filter(t -> toolName.equals(t.getName())).findFirst().orElse(null);
        }
    }

    /** A no-op IChatService for constructing the executor. */
    static class StubChatService implements IChatService {
        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(new ChatResponse());
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    private AiToolModel makeTool(String name, String... tags) {
        AiToolModel tool = new AiToolModel();
        tool.setName(name);
        tool.setDescription("Tool: " + name);
        if (tags.length > 0) {
            tool.setTags(Set.of(tags));
        }
        return tool;
    }

    private AiToolModel makeMetaTool(String name, String... tags) {
        AiToolModel tool = makeTool(name, tags);
        tool.setMeta(true);
        return tool;
    }

    private ReActAgentExecutor buildExecutor(IToolManager toolManager) {
        return ReActAgentExecutor.builder()
                .chatService(new StubChatService())
                .toolManager(toolManager)
                .build();
    }

    private List<String> toolNames(List<ChatToolDefinition> defs) {
        List<String> names = new ArrayList<>();
        for (ChatToolDefinition d : defs) {
            names.add(d.getName());
        }
        return names;
    }

    @Test
    void emptyActiveTagsAllDeclaredToolsVisible() {
        StubToolManager tm = new StubToolManager(Arrays.asList(
                makeTool("read-file", "readonly"),
                makeTool("delete-file", "admin"),
                makeTool("bash")));
        ReActAgentExecutor exec = buildExecutor(tm);

        AgentModel model = new AgentModel();
        // No activeTags → no tag filtering; no _tools → no name restriction
        List<ChatToolDefinition> defs = exec.buildToolDefinitions(model, null);

        assertEquals(3, defs.size());
        assertTrue(toolNames(defs).containsAll(Arrays.asList("read-file", "delete-file", "bash")));
    }

    @Test
    void activeTagsIntersectionFiltering() {
        StubToolManager tm = new StubToolManager(Arrays.asList(
                makeTool("read-file", "readonly"),
                makeTool("delete-file", "admin"),
                makeTool("bash", "readonly", "shell")));
        ReActAgentExecutor exec = buildExecutor(tm);

        AgentModel model = new AgentModel();
        model.setActiveTags(Set.of("readonly"));

        List<ChatToolDefinition> defs = exec.buildToolDefinitions(model, null);

        assertEquals(2, defs.size());
        assertTrue(toolNames(defs).contains("read-file"));
        assertTrue(toolNames(defs).contains("bash"));
        assertFalse(toolNames(defs).contains("delete-file"));
    }

    @Test
    void denyTagsRemovesMatchingTools() {
        StubToolManager tm = new StubToolManager(Arrays.asList(
                makeTool("read-file", "readonly"),
                makeTool("delete-file", "readonly", "admin"),
                makeTool("bash", "readonly")));
        ReActAgentExecutor exec = buildExecutor(tm);

        AgentModel model = new AgentModel();
        model.setDenyTags(Set.of("admin"));

        List<ChatToolDefinition> defs = exec.buildToolDefinitions(model, null);

        assertEquals(2, defs.size());
        assertFalse(toolNames(defs).contains("delete-file"));
        assertTrue(toolNames(defs).contains("read-file"));
    }

    @Test
    void denyToolsRemovesByName() {
        StubToolManager tm = new StubToolManager(Arrays.asList(
                makeTool("read-file", "readonly"),
                makeTool("delete-file", "readonly"),
                makeTool("bash", "readonly")));
        ReActAgentExecutor exec = buildExecutor(tm);

        AgentModel model = new AgentModel();
        model.setActiveTags(Set.of("readonly"));
        model.setDenyTools(Set.of("bash"));

        List<ChatToolDefinition> defs = exec.buildToolDefinitions(model, null);

        assertEquals(2, defs.size());
        assertTrue(toolNames(defs).contains("read-file"));
        assertFalse(toolNames(defs).contains("bash"));
    }

    @Test
    void metaToolAlwaysVisibleRegardlessOfActiveTags() {
        StubToolManager tm = new StubToolManager(Arrays.asList(
                makeTool("read-file", "readonly"),
                makeMetaTool("set-active-tags")));
        ReActAgentExecutor exec = buildExecutor(tm);

        AgentModel model = new AgentModel();
        model.setActiveTags(Set.of("admin")); // No tool has "admin" tag

        List<ChatToolDefinition> defs = exec.buildToolDefinitions(model, null);

        // Only the meta tool survives (read-file doesn't match "admin")
        assertEquals(1, defs.size());
        assertEquals("set-active-tags", defs.get(0).getName());
    }

    @Test
    void metaToolSurvivesDenyTags() {
        StubToolManager tm = new StubToolManager(Arrays.asList(
                makeTool("read-file", "readonly"),
                makeMetaTool("set-active-tags", "system")));
        ReActAgentExecutor exec = buildExecutor(tm);

        AgentModel model = new AgentModel();
        model.setDenyTags(Set.of("system")); // Would deny the meta tool if it weren't meta

        List<ChatToolDefinition> defs = exec.buildToolDefinitions(model, null);

        // Meta tool survives denyTags
        assertEquals(2, defs.size());
        assertTrue(toolNames(defs).contains("set-active-tags"));
        assertTrue(toolNames(defs).contains("read-file"));
    }

    @Test
    void runtimeSessionOverrideReflectedInFiltering() {
        StubToolManager tm = new StubToolManager(Arrays.asList(
                makeTool("read-file", "readonly"),
                makeTool("delete-file", "admin"),
                makeMetaTool("set-active-tags")));
        ReActAgentExecutor exec = buildExecutor(tm);

        AgentSession session = AgentSession.create("s1", "agent");
        AgentModel model = new AgentModel();

        // Initially no activeTags → all visible
        List<ChatToolDefinition> defs1 = exec.buildToolDefinitions(model, session);
        assertEquals(3, defs1.size());

        // Simulate set-active-tags switching to "admin"
        session.setActiveTags(Set.of("admin"));
        List<ChatToolDefinition> defs2 = exec.buildToolDefinitions(model, session);
        assertEquals(2, defs2.size());
        assertTrue(toolNames(defs2).contains("delete-file"));
        assertTrue(toolNames(defs2).contains("set-active-tags")); // meta always visible
        assertFalse(toolNames(defs2).contains("read-file"));

        // Clear active tags → all visible again
        session.setActiveTags(Set.of());
        List<ChatToolDefinition> defs3 = exec.buildToolDefinitions(model, session);
        assertEquals(3, defs3.size());
    }

    @Test
    void toolsWhitelistStillWorks() {
        StubToolManager tm = new StubToolManager(Arrays.asList(
                makeTool("read-file", "readonly"),
                makeTool("delete-file", "admin"),
                makeTool("bash", "readonly")));
        ReActAgentExecutor exec = buildExecutor(tm);

        AgentModel model = new AgentModel();
        model.setTools(Set.of("read-file", "bash")); // whitelist

        List<ChatToolDefinition> defs = exec.buildToolDefinitions(model, null);

        assertEquals(2, defs.size());
        assertTrue(toolNames(defs).contains("read-file"));
        assertTrue(toolNames(defs).contains("bash"));
        assertFalse(toolNames(defs).contains("delete-file"));
    }

    @Test
    void toolsWhitelistInteractsWithTagFilter() {
        StubToolManager tm = new StubToolManager(Arrays.asList(
                makeTool("read-file", "readonly"),
                makeTool("write-file", "readonly"),
                makeTool("delete-file", "admin")));
        ReActAgentExecutor exec = buildExecutor(tm);

        AgentModel model = new AgentModel();
        model.setTools(Set.of("read-file", "delete-file")); // whitelist
        model.setActiveTags(Set.of("readonly")); // tag filter

        List<ChatToolDefinition> defs = exec.buildToolDefinitions(model, null);

        // Only read-file: it's in the whitelist AND matches the activeTag
        assertEquals(1, defs.size());
        assertEquals("read-file", defs.get(0).getName());
    }
}
