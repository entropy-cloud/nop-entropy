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

/**
 * Session-scope regression tests for {@link UpdateTodosExecutor}: the todo
 * table is keyed by the session id carried in the execution context, different
 * sessions never share or corrupt each other's lists, and a missing session id
 * fails closed instead of silently falling back to a process-global table.
 */
public class UpdateTodosExecutorTest {
    private UpdateTodosExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new UpdateTodosExecutor();
    }

    @Test
    void testToolName() {
        assertEquals("update-todos", executor.getToolName());
    }

    @Test
    void testExecuteWithoutAction() {
        XNode node = XNode.make("update-todos");
        node.setAttr("id", "1");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext("s1")).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("action is required"));
    }

    @Test
    void testExecuteWithInvalidAction() {
        XNode node = XNode.make("update-todos");
        node.setAttr("id", "1");
        node.setAttr("action", "invalid");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext("s1")).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Invalid action"));
    }

    @Test
    void testReadEmptyTodoList() {
        XNode node = XNode.make("update-todos");
        node.setAttr("id", "1");
        node.setAttr("action", "read");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext("s-read-empty")).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("0 items"));
    }

    @Test
    void testWriteTodoList() {
        XNode node = XNode.make("update-todos");
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
        AiToolCallResult result = executor.executeAsync(call, new MockContext("s-write")).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
    }

    @Test
    void testReadAfterWrite() {
        XNode writeNode = XNode.make("update-todos");
        writeNode.setAttr("id", "1");
        writeNode.setAttr("action", "write");
        XNode todos = writeNode.makeChild("todos");
        XNode todo = todos.makeChild("todo");
        todo.setAttr("id", "1");
        todo.setAttr("content", "Test Task");
        todo.setAttr("status", "pending");
        todo.setAttr("priority", "high");
        executor.executeAsync(AiToolCall.fromNode(writeNode), new MockContext("s-read-after-write")).toCompletableFuture().join();

        XNode readNode = XNode.make("update-todos");
        readNode.setAttr("id", "2");
        readNode.setAttr("action", "read");
        AiToolCall readCall = AiToolCall.fromNode(readNode);
        AiToolCallResult result = executor.executeAsync(readCall, new MockContext("s-read-after-write")).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Test Task"));
    }

    @Test
    void testClearTodoList() {
        XNode writeNode = XNode.make("update-todos");
        writeNode.setAttr("id", "1");
        writeNode.setAttr("action", "write");
        writeNode.makeChild("todos");
        AiToolCall call = AiToolCall.fromNode(writeNode);
        AiToolCallResult result = executor.executeAsync(call, new MockContext("s-clear")).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("cleared"));
    }

    /**
     * Cross-session isolation: a write in session A is invisible to session B,
     * and session B's read is not polluted by A's list.
     */
    @Test
    void testCrossSessionIsolation() {
        XNode writeNode = XNode.make("update-todos");
        writeNode.setAttr("id", "1");
        writeNode.setAttr("action", "write");
        XNode todos = writeNode.makeChild("todos");
        XNode todo = todos.makeChild("todo");
        todo.setAttr("id", "1");
        todo.setAttr("content", "Session A secret task");
        todo.setAttr("status", "pending");
        todo.setAttr("priority", "high");
        AiToolCallResult writeResult = executor.executeAsync(
                AiToolCall.fromNode(writeNode), new MockContext("session-A")).toCompletableFuture().join();
        assertEquals("success", writeResult.getStatus());

        XNode readBNode = XNode.make("update-todos");
        readBNode.setAttr("id", "2");
        readBNode.setAttr("action", "read");
        AiToolCallResult readB = executor.executeAsync(
                AiToolCall.fromNode(readBNode), new MockContext("session-B")).toCompletableFuture().join();
        assertEquals("success", readB.getStatus());
        assertTrue(readB.getOutput().getBody().contains("0 items"),
                "session B must not see session A's todos, got: " + readB.getOutput().getBody());

        XNode readANode = XNode.make("update-todos");
        readANode.setAttr("id", "3");
        readANode.setAttr("action", "read");
        AiToolCallResult readA = executor.executeAsync(
                AiToolCall.fromNode(readANode), new MockContext("session-A")).toCompletableFuture().join();
        assertEquals("success", readA.getStatus());
        assertTrue(readA.getOutput().getBody().contains("Session A secret task"),
                "session A must still see its own todos, got: " + readA.getOutput().getBody());
    }

    /**
     * A session writing an empty list clears only its own list; other sessions
     * keep their content.
     */
    @Test
    void testWriteEmptyClearsOnlyOwnSession() {
        XNode writeNode = XNode.make("update-todos");
        writeNode.setAttr("id", "1");
        writeNode.setAttr("action", "write");
        XNode todos = writeNode.makeChild("todos");
        XNode todo = todos.makeChild("todo");
        todo.setAttr("id", "1");
        todo.setAttr("content", "Keep me");
        todo.setAttr("status", "pending");
        todo.setAttr("priority", "medium");
        executor.executeAsync(AiToolCall.fromNode(writeNode), new MockContext("session-K")).toCompletableFuture().join();

        XNode clearNode = XNode.make("update-todos");
        clearNode.setAttr("id", "2");
        clearNode.setAttr("action", "write");
        clearNode.makeChild("todos");
        AiToolCallResult clearResult = executor.executeAsync(
                AiToolCall.fromNode(clearNode), new MockContext("session-J")).toCompletableFuture().join();
        assertEquals("success", clearResult.getStatus());
        assertTrue(clearResult.getOutput().getBody().contains("cleared"));

        XNode readNode = XNode.make("update-todos");
        readNode.setAttr("id", "3");
        readNode.setAttr("action", "read");
        AiToolCallResult readResult = executor.executeAsync(
                AiToolCall.fromNode(readNode), new MockContext("session-K")).toCompletableFuture().join();
        assertTrue(readResult.getOutput().getBody().contains("Keep me"),
                "session K's list must survive another session's clear, got: " + readResult.getOutput().getBody());
    }

    /**
     * Fail-closed: a context without a session id must not read/write a
     * process-global table — both actions return an explicit error.
     */
    @Test
    void testNoSessionIdFailsClosed() {
        XNode writeNode = XNode.make("update-todos");
        writeNode.setAttr("id", "1");
        writeNode.setAttr("action", "write");
        XNode todos = writeNode.makeChild("todos");
        XNode todo = todos.makeChild("todo");
        todo.setAttr("id", "1");
        todo.setAttr("content", "must not land in a global table");
        todo.setAttr("status", "pending");
        todo.setAttr("priority", "high");
        AiToolCallResult writeResult = executor.executeAsync(
                AiToolCall.fromNode(writeNode), new MockContext(null)).toCompletableFuture().join();
        assertEquals("failure", writeResult.getStatus());
        assertTrue(writeResult.getError().getBody().contains("sessionId"),
                "write without session must fail closed, got: " + writeResult.getError().getBody());

        XNode readNode = XNode.make("update-todos");
        readNode.setAttr("id", "2");
        readNode.setAttr("action", "read");
        AiToolCallResult readResult = executor.executeAsync(
                AiToolCall.fromNode(readNode), new MockContext(null)).toCompletableFuture().join();
        assertEquals("failure", readResult.getStatus());
        assertTrue(readResult.getError().getBody().contains("sessionId"),
                "read without session must fail closed, got: " + readResult.getError().getBody());
    }

    static class MockContext implements IToolExecuteContext {
        private final String sessionId;

        MockContext(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public IToolFileSystem getFileSystem() { return null; }
        @Override public IThreadPoolExecutor getExecutor() { return SyncThreadPoolExecutor.INSTANCE; }
        @Override public String getSessionId() { return sessionId; }
    }
}