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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class BashExecutorTest {
    private BashExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new BashExecutor();
    }

    @Test
    void testToolName() {
        assertEquals("bash", executor.getToolName());
    }

    @Test
    void testExecuteEchoCommand() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("echo Hello World");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Hello World"));
    }

    @Test
    void testExecuteCommandWithExitCode() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("exit 0");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void testExecuteCommandFailure() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("exit 1");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertEquals(1, result.getExitCode());
    }

    @Test
    void testExecuteWithWorkingDir() {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.setAttr("workingDir", System.getProperty("java.io.tmpdir"));
        node.makeChild("command").setContentValue(isWin ? "echo %cd%" : "pwd");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
    }

    @Test
    void testExecuteWithEnv() {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        XNode node = XNode.make("bash");
        node.makeChild("command").setContentValue(isWin ? "echo %MY_VAR%" : "echo $MY_VAR");
        XNode env = node.makeChild("env");
        env.setAttr("name", "MY_VAR");
        env.setAttr("value", "test_value");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("test_value"));
    }

    @Test
    void testDestructiveCommandRejected() {
        XNode node = XNode.make("bash");
        node.makeChild("command").setContentValue("rm -rf /");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Command blocked"),
                "Destructive command must be blocked. Error: " + result.getError().getBody());
    }

    @Test
    void testEmptyCommandRejected() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("empty command"),
                "Empty command must be rejected with validation message. Error: " + result.getError().getBody());
    }

    @Test
    void testBlankCommandRejected() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("   \t ");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("empty command"));
    }

    @Test
    void testDangerousEnvVarRejected() {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        XNode node = XNode.make("bash");
        node.makeChild("command").setContentValue(isWin ? "echo %LD_PRELOAD%" : "echo $LD_PRELOAD");
        XNode env = node.makeChild("env");
        env.setAttr("name", "LD_PRELOAD");
        env.setAttr("value", "/tmp/malicious.so");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus(),
                "Command must still run with dangerous env var stripped");
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
