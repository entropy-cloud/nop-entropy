package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link SkillExecutor} that do not initialize the Nop VFS.
 * Without VFS initialization, {@code discoverSkills} hits its explicit failure path:
 * it logs a WARN and returns an empty skill list instead of fabricating phantom skills.
 */
public class SkillExecutorTest {
    private SkillExecutor executor;

    private static final String[] PHANTOM_SKILL_NAMES = {
            "log-analysis", "translator", "calculator", "code-review", "test-generator"
    };

    @BeforeEach
    void setUp() {
        executor = new SkillExecutor();
    }

    @Test
    void testToolName() {
        assertEquals("skill", executor.getToolName());
    }

    @Test
    void testExecuteWithoutAction() {
        XNode node = XNode.make("skill");
        node.setAttr("id", "1");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("action is required"));
    }

    @Test
    void testExecuteWithInvalidAction() {
        XNode node = XNode.make("skill");
        node.setAttr("id", "1");
        node.setAttr("action", "invalid");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Invalid action"));
    }

    @Test
    void testListSkillsReturnsNoPhantomSkillsOnVfsUnavailable() {
        // VFS is not initialized in this class, so discoverSkills must fail explicitly
        // (WARN log + empty list) instead of fabricating hardcoded phantom skills.
        XNode node = XNode.make("skill");
        node.setAttr("id", "1");
        node.setAttr("action", "list");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("<skills>"));
        for (String phantom : PHANTOM_SKILL_NAMES) {
            assertFalse(result.getOutput().getBody().contains(phantom),
                    "phantom skill '" + phantom + "' must not appear in list output");
        }
    }

    @Test
    void testLoadSkillWithoutName() {
        XNode node = XNode.make("skill");
        node.setAttr("id", "1");
        node.setAttr("action", "load");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("skillName is required"));
    }

    @Test
    void testLoadSkillNotFound() {
        XNode node = XNode.make("skill");
        node.setAttr("id", "1");
        node.setAttr("action", "load");
        node.setAttr("skillName", "nonexistent-skill");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Skill not found"));
    }

    @Test
    void testLoadFormerPhantomSkillFails() {
        // Regression: previously load("log-analysis") succeeded via the hardcoded
        // phantom-skill fallback. After the fix it must fail explicitly.
        XNode node = XNode.make("skill");
        node.setAttr("id", "1");
        node.setAttr("action", "load");
        node.setAttr("skillName", "log-analysis");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Skill not found"));
    }

    static class MockContext implements IToolExecuteContext {
        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public IToolFileSystem getFileSystem() { return null; }
        @Override public IThreadPoolExecutor getExecutor() { return SyncThreadPoolExecutor.INSTANCE; }
    }
}
