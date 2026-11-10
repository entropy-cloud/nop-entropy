package io.nop.ai.toolkit.tools.sandbox;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.2-C6-009（plan 2557 裁定 F，行为钉）：cancel 可重试——销毁段 UOE/SecurityException
 * 守卫下，销毁失败 → 注册项保留（putIfAbsent 回填）+ cancel-pending 标记 + rethrow
 * （boolean 外部契约保持：失败以异常表达，≠「无在途」）；二次 cancel 可重试；
 * remove 顺序外部契约不变。2501 safeClose 补偿在守卫降级路径下仍执行。
 */
public class TestBashCancelRetriable {

    /** 最小 Process stub：descendants() 走 JDK 默认实现（抛 UOE）覆盖守卫降级面。 */
    static class StubProcess extends Process {
        volatile boolean failDestroy;
        int destroyCalls;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            // unused by the tracker
        }

        @Override
        public Process destroyForcibly() {
            destroyCalls++;
            if (failDestroy) {
                throw new UnsupportedOperationException("kill denied (stub)");
            }
            return this;
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    }

    @Test
    void unknownCallIdReturnsFalse() {
        BashExecutionTracker tracker = new BashExecutionTracker();
        assertFalse(tracker.cancelExecution("nope"));
    }

    @Test
    void descendantsUoeIsDegradedButCancelSucceedsAndCompensationRuns() {
        BashExecutionTracker tracker = new BashExecutionTracker();
        StubProcess process = new StubProcess();
        // descendants() 默认抛 UOE（JDK ProcessHandle 契约）→ 守卫降级不阻断；
        // safeClose 补偿对 nullInputStream 仍执行（无异常即通过）
        AtomicReference<StringBuilder> captured = new AtomicReference<>(new StringBuilder("partial out"));
        tracker.register("call-1", process, captured, null);

        assertTrue(tracker.cancelExecution("call-1"),
                "descendants UOE must degrade (killTree-style), not fail the cancel");
        assertEquals(1, process.destroyCalls, "root destroyForcibly still attempted");
        // cancel 成功 → 注册项移除（remove 顺序外部契约不变）
        assertFalse(tracker.progressSnapshot("call-1").isInFlight());
    }

    @Test
    void destroyFailureRetainsEntryAndSecondCancelRetries() {
        BashExecutionTracker tracker = new BashExecutionTracker();
        StubProcess process = new StubProcess();
        process.failDestroy = true;
        AtomicReference<StringBuilder> captured = new AtomicReference<>(new StringBuilder("still running"));
        tracker.register("call-2", process, captured, null);

        // 第一次 cancel：销毁失败 → rethrow（异常表达失败，非 false）
        assertThrows(UnsupportedOperationException.class, () -> tracker.cancelExecution("call-2"));

        // 失败≠「无在途」：注册项已 putIfAbsent 回填，进度快照仍可寻址
        assertTrue(tracker.progressSnapshot("call-2").isInFlight(),
                "failed cancel must NOT look like 'no in-flight'");

        // 二次 cancel 可重试：销毁恢复后成功
        process.failDestroy = false;
        assertTrue(tracker.cancelExecution("call-2"));
        assertFalse(tracker.progressSnapshot("call-2").isInFlight(),
                "successful cancel removes the entry (remove-order contract unchanged)");
    }
}
