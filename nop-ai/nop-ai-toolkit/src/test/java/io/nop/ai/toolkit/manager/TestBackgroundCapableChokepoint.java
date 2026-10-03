package io.nop.ai.toolkit.manager;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolExecutorProvider;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2301 (WI25): the runInBackground chokepoint — only tools declaring
 * {@code <background-capable>true</background-capable>} may carry the
 * reserved argument; non-capable tools fail loud (explicit error result,
 * never a silent foreground run).
 */
public class TestBackgroundCapableChokepoint {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void bashDeclaresBackgroundCapable() {
        AiToolModel bash = new ToolManagerImpl().loadTool("bash");
        assertNotNull(bash);
        assertTrue(bash.isBackgroundCapable(),
                "bash.tool.xml must declare <background-capable>true</background-capable>");
    }

    @Test
    void nonCapableToolDefaultsFalse() {
        AiToolModel readFile = new ToolManagerImpl().loadTool("read-file");
        assertNotNull(readFile);
        assertFalse(readFile.isBackgroundCapable(),
                "absent declaration defaults to not background-capable");
    }

    @Test
    void runInBackgroundOnNonCapableToolIsRejected() {
        StubProvider.executions.set(0);
        ToolManagerImpl manager = new ToolManagerImpl(new StubProvider(), null);

        AiToolCallResult result = manager.callTool("read-file",
                callWithRunInBackground(), new PlainContext()).join();

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("does not support runInBackground"),
                result.getError().getBody());
        assertEquals(0, StubProvider.executions.get(),
                "the executor must never run for a rejected call");
    }

    @Test
    void runWithoutFlagPassesThrough() {
        StubProvider.executions.set(0);
        ToolManagerImpl manager = new ToolManagerImpl(new StubProvider(), null);

        AiToolCallResult result = manager.callTool("read-file",
                plainCall(), new PlainContext()).join();

        assertEquals("success", result.getStatus());
        assertEquals(1, StubProvider.executions.get(), "no flag → normal execution");
    }

    static AiToolCall callWithRunInBackground() {
        XNode node = XNode.make("read-file");
        node.setAttr("id", "1");
        node.setAttr("runInBackground", "true");
        node.makeChild("path").setContentValue("x.txt");
        return AiToolCall.fromNode(node);
    }

    static AiToolCall plainCall() {
        XNode node = XNode.make("read-file");
        node.setAttr("id", "2");
        node.makeChild("path").setContentValue("x.txt");
        return AiToolCall.fromNode(node);
    }

    static class StubProvider implements IToolExecutorProvider {
        static final java.util.concurrent.atomic.AtomicInteger executions =
                new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public IToolExecutor getExecutor(String toolName) {
            return new StubExecutor();
        }

        @Override
        public java.util.Collection<String> getToolNames() {
            return java.util.List.of("read-file");
        }
    }

    static class StubExecutor implements IToolExecutor {
        @Override
        public String getToolName() {
            return "read-file";
        }

        @Override
        public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
            return CompletableFuture.supplyAsync(() -> {
                StubProvider.executions.incrementAndGet();
                AiToolCallResult result = new AiToolCallResult();
                result.setId(call.getId());
                result.setStatus("success");
                return result;
            });
        }
    }

    static class PlainContext implements IToolExecuteContext {
        @Override public java.io.File getWorkDir() {
            return new java.io.File(".");
        }

        @Override public Map<String, String> getEnvs() {
            return Map.of();
        }

        @Override public long getExpireAt() {
            return Long.MAX_VALUE;
        }

        @Override public io.nop.api.core.util.ICancelToken getCancelToken() {
            return null;
        }

        @Override public io.nop.ai.toolkit.fs.IToolFileSystem getFileSystem() {
            return null;
        }

        @Override public io.nop.commons.concurrent.executor.IThreadPoolExecutor getExecutor() {
            return io.nop.commons.concurrent.executor.SyncThreadPoolExecutor.INSTANCE;
        }
    }
}
