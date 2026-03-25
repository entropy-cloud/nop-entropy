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

public class ManageTodoListExecutorTest {
    private ManageTodoListExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ManageTodoListExecutor();
    }

    @Test
    void testToolName() {
        assertEquals("manage-todo-list", executor.getToolName());
    }

    @Test
    void testExecuteWithoutAction() {
        XNode node = XNode.make("manage-todo-list");
        node.setAttr("id", "1");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("action is required"));
    }

    @Test
    void testExecuteWithInvalidAction() {
        XNode node = XNode.make("manage-todo-list");
        node.setAttr("id", "1");
        node.setAttr("action", "invalid");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Invalid action"));
    }

    @Test
    void testReadEmptyTodoList() {
        XNode node = XNode.make("manage-todo-list");
        node.setAttr("id", "1");
        node.setAttr("action", "read");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
    }

    @Test
    void testWriteTodoList() {
        XNode node = XNode.make("manage-todo-list");
        node.setAttr("id", "1");
        node.setAttr("action", "write");
        XNode todos = node.makeChild("todos");
        XNode todo1 = todos.makeChild("todo");
        todo1.setAttr("id", "1");
        todo1.setAttr("content", "Task 1");
        todo1.setAttr("status", "pending");
        todo1.setAttr("priority", "high");
        XNode todo2 = todos.makeChild("todo");
        todo2.setAttr("id", "2");
        todo2.setAttr("content", "Task 2");
        todo2.setAttr("status", "completed");
        todo2.setAttr("priority", "medium");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
    }

    @Test
    void testReadAfterWrite() {
        XNode writeNode = XNode.make("manage-todo-list");
        writeNode.setAttr("id", "1");
        writeNode.setAttr("action", "write");
        XNode todos = writeNode.makeChild("todos");
        XNode todo = todos.makeChild("todo");
        todo.setAttr("id", "1");
        todo.setAttr("content", "Test Task");
        todo.setAttr("status", "pending");
        todo.setAttr("priority", "high");
        executor.executeAsync(AiToolCall.fromNode(writeNode), new MockContext()).toCompletableFuture().join();

        XNode readNode = XNode.make("manage-todo-list");
        readNode.setAttr("id", "2");
        readNode.setAttr("action", "read");
        AiToolCall readCall = AiToolCall.fromNode(readNode);
        AiToolCallResult result = executor.executeAsync(readCall, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Test Task"));
    }

    @Test
    void testClearTodoList() {
        XNode writeNode = XNode.make("manage-todo-list");
        writeNode.setAttr("id", "1");
        writeNode.setAttr("action", "write");
        writeNode.makeChild("todos");
        AiToolCall call = AiToolCall.fromNode(writeNode);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("cleared"));
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
