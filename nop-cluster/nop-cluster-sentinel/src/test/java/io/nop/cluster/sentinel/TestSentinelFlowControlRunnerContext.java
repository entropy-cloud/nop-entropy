package io.nop.cluster.sentinel;

import io.nop.api.core.util.FutureHelper;
import io.nop.rpc.model.FlowControlEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P1-4.4-C3（plan 2395）：SentinelFlowControlRunner.runAsync 的 ContextUtil.enter/exit
 * 必须无条件配对——修复前成功路径 proceedCalled=true 使 finally 的有条件 exit 永不执行，
 * 发起线程 context 栈持续增长（泄漏）。entry 的 exit 与 context 的 exit 配对独立
 * （entry 在 whenComplete/失败分支退出；context 属发起线程，同步段内退出）。
 */
public class TestSentinelFlowControlRunnerContext {

    private final SentinelFlowControlRunner runner = new SentinelFlowControlRunner();

    private FlowControlEntry entry(String resource) {
        FlowControlEntry entry = new FlowControlEntry();
        entry.setContextName("test-context");
        entry.setOrigin("test");
        entry.setResource(resource);
        return entry;
    }

    @AfterEach
    void cleanupContext() {
        // 防御：若实现回归泄漏，清掉本线程残留避免影响其他测试
        while (com.alibaba.csp.sentinel.context.ContextUtil.getCurEntry() != null) {
            com.alibaba.csp.sentinel.context.ContextUtil.exit();
        }
    }

    /** 正常完成路径：runAsync 返回后发起线程不得残留 context（修复前泄漏非 null）。 */
    @Test
    public void testContextReleasedAfterSuccessfulRun() {
        assertEquals("ok", FutureHelper.syncGet(
                runner.runAsync(entry("res-success"), () -> CompletableFuture.completedFuture("ok"))));

        assertNull(com.alibaba.csp.sentinel.context.ContextUtil.getCurEntry(),
                "initiator thread context must be released after runAsync (P1-4.4-C3)");
    }

    /** 多次调用后 context 栈不累积（泄漏放大面）。 */
    @Test
    public void testRepeatedRunsDoNotLeakContext() {
        for (int i = 0; i < 100; i++) {
            FutureHelper.syncGet(runner.runAsync(entry("res-loop"), () -> CompletableFuture.completedFuture(1)));
        }
        assertNull(com.alibaba.csp.sentinel.context.ContextUtil.getCurEntry(),
                "repeated runAsync must not accumulate context frames (P1-4.4-C3)");
    }
}
