package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.tools.sandbox.HostBashSandbox;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.2-C4-007（plan 2557 裁定 D，行为钉）：submit→run 回滚——registry.run 抛
 * {@link UnsupportedOperationException}（IBackgroundTaskRegistry default 契约）
 * 时，submit 已建的 RUNNING 条目必须回滚为 FAILED 终态（RUNNING 永久滞留面消除），
 * 调用面得到显式错误结果而非「已启动」假确认。
 */
public class TestBashExecutorRunRollback {

    /** run() 抛 UOE 的 registry（无 background executor 的实现形态）。 */
    static class RunUnsupportedRegistry extends TestBashExecutorBackgroundMode.StubRegistry {
        final AtomicInteger runAttempts = new AtomicInteger();

        RunUnsupportedRegistry() {
            super(null);
        }

        @Override
        public void run(String taskId, Runnable body) {
            runAttempts.incrementAndGet();
            throw new UnsupportedOperationException(
                    "IBackgroundTaskRegistry.run is not supported by this implementation");
        }
    }

    @Test
    void runUoeRollsRunningBackToFailed() {
        RunUnsupportedRegistry registry = new RunUnsupportedRegistry();
        BashExecutor executor = new BashExecutor();
        // wire a real sandbox (rollback happens before execute; sandbox only must be non-null)
        executor.setSandbox(new HostBashSandbox());

        AiToolCallResult result = executor.executeAsync(
                TestBashExecutorBackgroundMode.backgroundCall("echo hi", null),
                new TestBashExecutorBackgroundMode.ContextWithRegistry(registry)
        ).toCompletableFuture().join();

        assertEquals(1, registry.runAttempts.get(), "run was attempted exactly once");
        assertEquals("failure", result.getStatus(), "caller must see an explicit error, not a started-confirmation");

        String taskId = registry.snapshots.keySet().iterator().next();
        IBackgroundTaskRegistry.TaskSnapshot snapshot = registry.snapshot(taskId);
        assertNotNull(snapshot);
        assertEquals(IBackgroundTaskRegistry.STATUS_FAILED, snapshot.getStatus(),
                "RUNNING entry must be rolled back to FAILED (no permanent RUNNING residue)");
        assertTrue(result.getError().getBody().contains("rolled back"),
                "error surfaces the rollback: " + result.getError().getBody());
    }
}
