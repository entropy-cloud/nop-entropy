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

public class CallAgentExecutorTest {
    private CallAgentExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CallAgentExecutor();
    }

    @Test
    void testToolName() {
        assertEquals("call-agent", executor.getToolName());
    }

    @Test
    void testExecuteWithoutAgentId() {
        XNode node = XNode.make("call-agent");
        node.setAttr("id", "1");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("agentId is required"));
    }

    @Test
    void testExecuteWithAgentId() {
        XNode node = XNode.make("call-agent");
        node.setAttr("id", "1");
        node.setAttr("agentId", "test-agent");
        node.makeChild("input").setContentValue("Test prompt");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("test-agent"));
    }

    @Test
    void testExecuteWithSelfAgent() {
        XNode node = XNode.make("call-agent");
        node.setAttr("id", "1");
        node.setAttr("agentId", "self");
        node.makeChild("input").setContentValue("Test prompt");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("sessionId"));
    }

    @Test
    void testExecuteWithExistingSession() {
        XNode node1 = XNode.make("call-agent");
        node1.setAttr("id", "1");
        node1.setAttr("agentId", "test-agent");
        node1.makeChild("input").setContentValue("First call");
        AiToolCall call1 = AiToolCall.fromNode(node1);
        AiToolCallResult result1 = executor.executeAsync(call1, new MockContext()).toCompletableFuture().join();
        String sessionId = ((io.nop.ai.toolkit.model.AiAgentCallResult) result1).getSessionId();

        XNode node2 = XNode.make("call-agent");
        node2.setAttr("id", "2");
        node2.setAttr("agentId", "test-agent");
        node2.setAttr("sessionId", sessionId);
        node2.makeChild("input").setContentValue("Second call");
        AiToolCall call2 = AiToolCall.fromNode(node2);
        AiToolCallResult result2 = executor.executeAsync(call2, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result2.getStatus());
    }

    @Test
    void testExecuteWithSkills() {
        XNode node = XNode.make("call-agent");
        node.setAttr("id", "1");
        node.setAttr("agentId", "test-agent");
        node.setAttr("skills", "skill1,skill2");
        node.makeChild("input").setContentValue("Test prompt");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
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
