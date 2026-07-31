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

import static org.junit.jupiter.api.Assertions.*;

public class AskOracleExecutorTest {
    private AskOracleExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AskOracleExecutor();
    }

    @Test
    void testToolName() {
        assertEquals("ask-oracle", executor.getToolName());
    }

    @Test
    void testExecuteWithoutQuestion() {
        XNode node = XNode.make("ask-oracle");
        node.setAttr("id", "1");
        XNode options = node.makeChild("options");
        XNode opt = options.makeChild("option");
        opt.setAttr("key", "a");
        opt.setContentValue("Option A");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext(Map.of())).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Question is required"));
    }

    @Test
    void testExecuteWithoutOptions() {
        XNode node = XNode.make("ask-oracle");
        node.setAttr("id", "1");
        node.makeChild("question").setContentValue("What should I do?");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext(Map.of())).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("At least one option is required"));
    }

    @Test
    void testExecuteWithQuestionAndOptionsWithoutEndpoint() {
        XNode node = XNode.make("ask-oracle");
        node.setAttr("id", "1");
        node.makeChild("question").setContentValue("What should I do?");
        XNode options = node.makeChild("options");
        XNode opt1 = options.makeChild("option");
        opt1.setAttr("key", "a");
        opt1.setContentValue("Option A");
        XNode opt2 = options.makeChild("option");
        opt2.setAttr("key", "b");
        opt2.setContentValue("Option B");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext(Map.of())).toCompletableFuture().join();
        // P2-MA1-011: without ORACLE_ENDPOINT the executor must fail fast instead of
        // silently returning the first option as a fake success.
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("ORACLE_ENDPOINT"));
    }

    @Test
    void testExecuteWithEndpointFailsFastNotImplemented() {
        XNode node = XNode.make("ask-oracle");
        node.setAttr("id", "1");
        node.makeChild("question").setContentValue("What should I do?");
        XNode options = node.makeChild("options");
        XNode opt1 = options.makeChild("option");
        opt1.setAttr("key", "a");
        opt1.setContentValue("Option A");
        XNode opt2 = options.makeChild("option");
        opt2.setAttr("key", "b");
        opt2.setContentValue("Option B");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext(Map.of("ORACLE_ENDPOINT", "https://oracle.example.com")))
                .toCompletableFuture().join();
        // P2-MA1-011: even with the endpoint set, the oracle client call is not implemented —
        // the executor must fail fast instead of returning the first option.
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("not implemented"));
    }

    static class MockContext implements IToolExecuteContext {
        private final Map<String, String> envs;

        MockContext(Map<String, String> envs) {
            this.envs = envs;
        }

        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return envs; }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public IToolFileSystem getFileSystem() { return null; }
        @Override public IThreadPoolExecutor getExecutor() { return SyncThreadPoolExecutor.INSTANCE; }
    }
}
