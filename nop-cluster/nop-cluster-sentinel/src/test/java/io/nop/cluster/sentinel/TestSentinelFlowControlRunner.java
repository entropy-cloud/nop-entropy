package io.nop.cluster.sentinel;

import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.rpc.api.RpcErrors;
import io.nop.rpc.api.flowcontrol.FlowControlEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * P1-4.2-004（plan 2407）：SentinelFlowControlRunner entry/exit 配对与
 * BlockException→错误码映射直接测试（R1.29 仅覆盖 context 泄漏判别）。
 * 覆盖五种 task 路径的语义差异 + private newError 四态映射表。
 */
public class TestSentinelFlowControlRunner {

    private final SentinelFlowControlRunner runner = new SentinelFlowControlRunner();

    private FlowControlEntry entry(String resource) {
        FlowControlEntry entry = new FlowControlEntry();
        entry.setContextName("test-context");
        entry.setOrigin("test");
        entry.setResource(resource);
        return entry;
    }

    /** 反射读取 sentinel 线程上下文（1.8.6 无 getCurEntry 公开面，先例 R1.29）。 */
    private static Object currentContext() throws Exception {
        Field f = com.alibaba.csp.sentinel.context.ContextUtil.class.getDeclaredField("contextHolder");
        f.setAccessible(true);
        ThreadLocal<?> holder = (ThreadLocal<?>) f.get(null);
        return holder == null ? null : holder.get();
    }

    @AfterEach
    void cleanup() throws Exception {
        FlowRuleManager.loadRules(Collections.emptyList());
        while (currentContext() != null) {
            com.alibaba.csp.sentinel.context.ContextUtil.exit();
        }
    }

    /** 路径 1：立即完成 stage——值送达 + 发起线程 context 不残留（entry exit 在 whenComplete）。 */
    @Test
    public void testImmediateCompletionPairsEntryExit() throws Exception {
        assertEquals("ok", FutureHelper.syncGet(
                runner.runAsync(entry("res-immediate"), () -> CompletableFuture.completedFuture("ok"))));
        assertNull(currentContext(), "initiator thread must not retain context after completion");
    }

    /** 路径 2：异步完成 stage——完成前发起线程 context 已退出（同步段），完成后值送达。 */
    @Test
    public void testAsyncCompletionPairsEntryExit() throws Exception {
        CompletableFuture<String> stage = new CompletableFuture<>();
        CompletionStage<String> ret = runner.runAsync(entry("res-async"), () -> stage);

        // runAsync 返回即同步段结束——context 必须已退出（先于 stage 完成）
        assertNull(currentContext(), "context must be released when runAsync returns, before stage completes");
        assertTrue(!ret.toCompletableFuture().isDone(), "stage must not be completed synchronously");

        stage.complete("done");
        assertEquals("done", FutureHelper.syncGet(ret));
    }

    /** 路径 3：stage 异常完成——原异常经返回的 stage 传播 + context 无残留。 */
    @Test
    public void testStageFailurePropagatesViaStage() {
        RuntimeException boom = new IllegalStateException("task-stage-failed");
        CompletableFuture<String> stage = new CompletableFuture<>();
        CompletionStage<String> ret = runner.runAsync(entry("res-stage-fail"), () -> stage);
        stage.completeExceptionally(boom);

        // FutureHelper.syncGet 解包 CompletionException——原异常直接抛出
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> FutureHelper.syncGet(ret));
        assertSame(boom, thrown, "original stage exception must propagate unwrapped");
    }

    /**
     * 路径 4（反向钉#1，4.2-MA-05 plan 2559 裁定 B / C3-008 同 diff 改写）：
     * task supplier 同步抛出——runAsync 不再裸抛，而是返回已完成 failed future
     * （FutureHelper.reject），异常不包装；降级统计对称（C8-008）：traceEntry 计入
     * 异常计数，发起线程 context 不残留。
     */
    @Test
    public void testSupplierSyncThrowReturnsFailedFutureWithTraceCounted() throws Exception {
        RuntimeException boom = new IllegalArgumentException("supplier-sync-throw");
        AtomicBoolean called = new AtomicBoolean();

        ClusterNode node = ClusterBuilderSlot.getClusterNode("res-sync-throw");
        long exceptionsBefore = node == null ? 0 : node.totalException();

        CompletionStage<String> ret = runner.runAsync(entry("res-sync-throw"), () -> {
            called.set(true);
            throw boom;
        });

        assertTrue(called.get());
        CompletableFuture<String> cf = ret.toCompletableFuture();
        assertTrue(cf.isDone(), "sync-throw must return an already-completed failed future (FutureHelper.reject), not throw");
        // 原异常经 failed future 传播且不包装（CompletionException 解包后同 identity）
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> FutureHelper.syncGet(cf));
        assertSame(boom, thrown, "supplier exception must propagate via failed future, unwrapped");
        // C8-008 统计对称：sync-throw 路径 traceEntry 计入异常计数
        ClusterNode nodeAfter = ClusterBuilderSlot.getClusterNode("res-sync-throw");
        assertNotNull(nodeAfter, "entry was created, cluster node must exist");
        assertTrue(nodeAfter.totalException() > exceptionsBefore,
                "sync-throw path must traceEntry the exception (degrade statistics symmetric)");
        assertNull(currentContext(), "sync-throw path must still release context via finally");
    }

    /** 路径 5：block——FlowRule(QPS=0) 下返回 rejected future，错误码 ERR_RPC_FLOW_CONTROL_BLOCK。 */
    @Test
    public void testBlockReturnsRejectedFutureWithBlockErrorCode() throws Exception {
        FlowRule rule = new FlowRule();
        rule.setResource("res-blocked");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(Collections.singletonList(rule));

        CompletionStage<String> ret = runner.runAsync(entry("res-blocked"),
                () -> CompletableFuture.completedFuture("never"));

        assertTrue(ret.toCompletableFuture().isDone(), "block path must return an already-rejected future");
        try {
            FutureHelper.syncGet(ret);
            fail("block path must reject");
        } catch (Exception e) {
            RuntimeException re = NopException.adapt(e);
            assertTrue(re instanceof NopException, "block must reject with NopException, got " + re);
            assertEquals(RpcErrors.ERR_RPC_FLOW_CONTROL_BLOCK.getErrorCode(), ((NopException) re).getErrorCode(),
                    "block must map to ERR_RPC_FLOW_CONTROL_BLOCK");
        }
        assertNull(currentContext(), "block path must release context via finally");
    }

    /**
     * 路径 6（4.2-MA-05 plan 2559 裁定 B / C3-007）：task stage 永不完成——orTimeout
     * 兜底使 returned future 在配置超时后异常完成（TimeoutException），entry 经兜底
     * 回调 exit（curThreadNum 回落 0，非泄漏悬挂）。
     */
    @Test
    public void testNeverCompletingStageTimesOutAndExitsEntry() throws Exception {
        runner.setTaskTimeoutMillis(200);
        ClusterNode node = ClusterBuilderSlot.getClusterNode("res-timeout-leak");
        long threadsBefore = node == null ? 0 : node.curThreadNum();

        CompletableFuture<String> never = new CompletableFuture<>();
        CompletionStage<String> ret = runner.runAsync(entry("res-timeout-leak"), () -> never);

        CompletableFuture<String> cf = ret.toCompletableFuture();
        ExecutionException timeoutEx = assertThrows(ExecutionException.class, () -> cf.get(5, TimeUnit.SECONDS),
                "never-completing task must be bounded by orTimeout fallback");
        assertInstanceOf(TimeoutException.class, timeoutEx.getCause(),
                "timeout fallback must complete the future with TimeoutException");
        assertTrue(cf.isDone(), "returned future must complete by timeout fallback, not leak");

        // 兜底 exit：entry 线程计数回落（修复前 entry 永不 exit）
        ClusterNode nodeAfter = ClusterBuilderSlot.getClusterNode("res-timeout-leak");
        assertNotNull(nodeAfter);
        long expected = threadsBefore + 1;
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertTrue(nodeAfter.curThreadNum() <= expected, "thread count sanity"),
                () -> assertEquals(0, nodeAfter.curThreadNum(),
                        "entry must be exited by timeout fallback (curThreadNum back to 0)"));
        assertNull(currentContext(), "timeout fallback must not retain initiator context");
    }

    /** newError 四态映射表（private，反射调用；R1.29 反射判别先例）。 */
    @Test
    public void testNewErrorMapsAllBlockExceptionTypes() throws Exception {
        Method newError = SentinelFlowControlRunner.class.getDeclaredMethod("newError", com.alibaba.csp.sentinel.slots.block.BlockException.class);
        newError.setAccessible(true);

        // Degrade → ERR_RPC_FLOW_CONTROL_DEGRADE（sentinel 1.8.6 (String,String) 构造器首参即 ruleLimitApp——实测）
        DegradeException degradeEx = new DegradeException("app-d", "degrade-message");
        NopException degrade = (NopException) newError.invoke(runner, degradeEx);
        assertEquals(RpcErrors.ERR_RPC_FLOW_CONTROL_DEGRADE.getErrorCode(), degrade.getErrorCode());
        assertEquals("app-d", degrade.getParam("ruleLimitApp"));
        assertEquals(degradeEx.getMessage(), degrade.getParam("msg"));

        // Authority → ERR_RPC_FLOW_CONTROL_AUTHORITY
        AuthorityException authorityEx = new AuthorityException("app-a", "authority-message");
        NopException authority = (NopException) newError.invoke(runner, authorityEx);
        assertEquals(RpcErrors.ERR_RPC_FLOW_CONTROL_AUTHORITY.getErrorCode(), authority.getErrorCode());
        assertEquals("app-a", authority.getParam("ruleLimitApp"));

        // SystemBlock → ERR_RPC_FLOW_CONTROL_SYS + limitType。
        // sentinel 1.8.6 实测：SystemBlockException(String,String) 把 limitType 存入
        // BlockException.ruleLimitApp 字段，getLimitType() 直接委托 getRuleLimitApp()——
        // 两 param 恒同值，这是 sentinel 该版本的真实契约（javap -c 实证）。
        SystemBlockException sysEx = new SystemBlockException("res-s", "qps");
        NopException sys = (NopException) newError.invoke(runner, sysEx);
        assertEquals(RpcErrors.ERR_RPC_FLOW_CONTROL_SYS.getErrorCode(), sys.getErrorCode());
        assertEquals("qps", sys.getParam("ruleLimitApp"));
        assertEquals("qps", sys.getParam("limitType"));

        // Flow（其余 Block 兜底） → ERR_RPC_FLOW_CONTROL_BLOCK
        com.alibaba.csp.sentinel.slots.block.flow.FlowException flowEx =
                new com.alibaba.csp.sentinel.slots.block.flow.FlowException("app-f", "flow-message");
        NopException block = (NopException) newError.invoke(runner, flowEx);
        assertEquals(RpcErrors.ERR_RPC_FLOW_CONTROL_BLOCK.getErrorCode(), block.getErrorCode());
        assertEquals("app-f", block.getParam("ruleLimitApp"));

        assertNotNull(degrade.getParam("msg"));
    }
}
