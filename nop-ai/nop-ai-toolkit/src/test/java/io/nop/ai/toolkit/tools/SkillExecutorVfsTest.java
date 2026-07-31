package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring verification for {@link SkillExecutor}: with the Nop VFS initialized,
 * {@code list}/{@code load} must discover skills from the real VFS resource
 * directory {@code _vfs/nop/skills/} (see src/test/resources) — not from
 * hardcoded fallbacks. Runs in its own test class so the VFS lifecycle does not
 * leak into the plain unit tests of {@link SkillExecutorTest} (MA5.6-AR-1).
 */
public class SkillExecutorVfsTest {

    private static final String TEST_SKILL = "sample-analysis";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private SkillExecutor newExecutor() {
        return new SkillExecutor();
    }

    @Test
    void testListFindsVfsSkill() {
        XNode node = XNode.make("skill");
        node.setAttr("id", "1");
        node.setAttr("action", "list");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = newExecutor().executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("<skills>"));
        assertTrue(result.getOutput().getBody().contains(TEST_SKILL),
                "list output must contain the real VFS skill '" + TEST_SKILL + "', got: "
                        + result.getOutput().getBody());
    }

    @Test
    void testLoadVfsSkillSucceeds() {
        XNode node = XNode.make("skill");
        node.setAttr("id", "1");
        node.setAttr("action", "load");
        node.setAttr("skillName", TEST_SKILL);
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = newExecutor().executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("loaded successfully"));
    }

    @Test
    void testLoadUnknownSkillFails() {
        XNode node = XNode.make("skill");
        node.setAttr("id", "1");
        node.setAttr("action", "load");
        node.setAttr("skillName", "no-such-skill");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = newExecutor().executeAsync(call, new MockContext()).toCompletableFuture().join();
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
