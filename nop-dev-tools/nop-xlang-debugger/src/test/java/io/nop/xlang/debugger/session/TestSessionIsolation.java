/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.Breakpoint;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.api.core.util.SourceLocation.fromLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双会话隔离验证（roadmap item 9 验收：断点表/挂起/单步/求值四维互不干扰）。
 * 挂起编排复用 TestXLangDebugger 模式（后台线程直驱 checkBreakpoint）。
 *
 * <p>会话内多线程单步的定向语义重建归 item 10（plan 2468 Deferred 登记）；
 * 本测试验证跨会话隔离（每会话独立实例的单步/挂起互不影响）。
 */
public class TestSessionIsolation {
    private SessionDebugManager manager;

    @BeforeEach
    public void setUp() {
        manager = SessionDebugManager.instance();
        manager.init(new XLangDebugger());
    }

    @AfterEach
    public void tearDown() {
        DebugSessionScope.clear();
        manager.reset();
    }

    private static EvalRuntime newRt() {
        IEvalScope scope = XLang.newEvalScope();
        EvalRuntime rt = new EvalRuntime(scope);
        rt.pushFrame(new io.nop.core.lang.eval.EvalFrame(null, new String[]{"x"}, new Object[]{"x1"}));
        return rt;
    }

    /** 轮询等待 debugger 进入挂起态（超时失败）。 */
    private static void awaitSuspended(XLangDebugger debugger) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!debugger.isSuspended()) {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("debugger 未在 5s 内挂起");
            Thread.sleep(20);
        }
    }

    /**
     * 轮询等待 debugger 完成 step 落点重挂起（确定性等待）：挂起态短暂消失（step 释放
     * 返回后重挂起前的瞬态）期间继续等待，直到重新挂起且持续到下一次轮询仍挂起。
     */
    private static void awaitResuspendedAfterStep(XLangDebugger debugger) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        boolean sawTransientRelease = false;
        while (System.currentTimeMillis() < deadline) {
            if (!debugger.isSuspended()) {
                sawTransientRelease = true;
            } else if (sawTransientRelease) {
                return; // 释放后重新挂起 = step 落点已到达
            }
            Thread.sleep(20);
        }
        throw new AssertionError("step 后未在 5s 内重新挂起");
    }

    @Test
    public void testDualSessionIsolation() throws Exception {
        XLangDebugger debuggerA = new XLangDebugger();
        XLangDebugger debuggerB = new XLangDebugger();
        debuggerA.setUseExternalPath(false);
        debuggerB.setUseExternalPath(false);

        manager.createSession("A", debuggerA);
        manager.createSession("B", debuggerB);

        // 断点表隔离：A 设断点，B 无感知
        debuggerA.addBreakpoint(Breakpoint.build("a", 1));
        assertEquals(1, debuggerA.getBreakpoints().size());
        assertEquals(0, debuggerB.getBreakpoints().size());

        AtomicReference<Throwable> errorA = new AtomicReference<>();
        Thread threadA = new Thread(() -> {
            try {
                // A 命中断点 → 挂起阻塞
                debuggerA.checkBreakpoint(fromLine("a", 1), newRt());
            } catch (Throwable t) {
                errorA.set(t);
            }
        });
        threadA.start();
        awaitSuspended(debuggerA);

        // 挂起态隔离：A 挂起时 B 完全未挂起
        assertTrue(debuggerA.isSuspended());
        assertFalse(debuggerB.isSuspended());

        // 求值隔离：B 上执行求值不命中 A 的断点、不受 A 挂起影响
        AtomicReference<Throwable> errorB = new AtomicReference<>();
        Thread threadB = new Thread(() -> {
            try {
                // B 无断点：checkBreakpoint 立即返回（不挂起）
                debuggerB.checkBreakpoint(fromLine("b", 1), newRt());
            } catch (Throwable t) {
                errorB.set(t);
            }
        });
        threadB.start();
        threadB.join(5000);
        assertFalse(threadB.isAlive(), "B 的执行不得被 A 的挂起阻塞");
        assertEquals(null, errorB.get());

        // 单步隔离：A 的 stepInto 只释放 A 的挂起线程（每实例 stepMode/monitorLock），
        // 对 B 无任何影响（B 从未挂起）
        debuggerA.stepInto();
        threadA.join(5000);
        assertFalse(threadA.isAlive(), "A 的 stepInto 应释放 A 的挂起线程");
        assertFalse(debuggerB.isSuspended());

        // 清理
        if (errorA.get() != null)
            throw new AssertionError("A 线程异常", errorA.get());
    }

    @Test
    public void testDefaultSessionFullDebugChain() throws Exception {
        // IDEA 默认会话全链路（EC2）：manager 默认会话实例上的
        // addBreakpoint → 挂起 → stepInto → 再挂起 → resume 全链路。
        // CollectNotifier 的 BlockingQueue 提供确定性编排（stepInto 事件在重挂起前同步发出）。
        DebugSessionHandle defaultHandle = manager.getSession("default");
        assertNotNull(defaultHandle);
        XLangDebugger defaultDebugger = defaultHandle.getDebugger();
        defaultDebugger.setUseExternalPath(false);
        io.nop.xlang.debugger.CollectNotifier notifier = new io.nop.xlang.debugger.CollectNotifier();
        defaultDebugger.setNotifier(notifier);
        defaultDebugger.addBreakpoint(io.nop.api.debugger.Breakpoint.build("a", 1));

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                defaultDebugger.checkBreakpoint(fromLine("a", 1), newRt());
                defaultDebugger.checkBreakpoint(fromLine("a", 2), newRt());
                defaultDebugger.checkBreakpoint(fromLine("a", 3), newRt());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                notifier.getMessages().add("end");
            }
        });
        thread.start();

        java.util.concurrent.BlockingQueue<String> messages = notifier.getMessages();
        assertEquals("break:[1:0:0:0]a", messages.take());
        assertTrue(defaultDebugger.isSuspended());

        defaultDebugger.stepInto();
        assertEquals("stepInto:[2:0:0:0]a", messages.take());
        assertTrue(defaultDebugger.isSuspended());

        defaultDebugger.resume();
        assertEquals("end", messages.take());
        assertFalse(defaultDebugger.isSuspended());
        assertEquals(null, error.get());
    }

    @Test
    public void testCreateVsShutdownConcurrencyNoLeak() throws Exception {
        // 并发 create-vs-shutdown：同监视器锁纪律下，shutdown 后无存活会话泄漏
        // （每实例一个 cleanup 线程池，泄漏即线程泄漏）
        SessionDebugManager m = SessionDebugManager.instance();
        m.init(new XLangDebugger());
        List<DebugSessionHandle> created = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Boolean> rejected = new AtomicReference<>(false);
        Thread creator = new Thread(() -> {
            int i = 0;
            try {
                while (true) {
                    created.add(m.createSession("race-" + (i++)));
                }
            } catch (IllegalStateException shutdown) {
                rejected.set(true); // shutdown 后拒绝——预期出口
            }
        });
        creator.start();
        Thread.sleep(50);
        m.shutdownAll();
        creator.join(5000);
        assertFalse(creator.isAlive(), "shutdown 后创建线程必须退出");
        assertEquals(true, rejected.get());

        // 无泄漏：全部已创建会话均 closed + destroyed
        for (DebugSessionHandle handle : created) {
            assertTrue(handle.isDestroyed());
            assertTrue(handle.getDebugger().isClosed());
        }
        // 恢复供后续用例（每个测试独立 setUp/tearDown，这里手动复位）
        m.reset();
        m.init(new XLangDebugger());
    }

    @Test
    public void testBreakpointIsolationViaManagerHandles() throws Exception {
        XLangDebugger debuggerA = new XLangDebugger();
        XLangDebugger debuggerB = new XLangDebugger();
        manager.createSession("A", debuggerA);
        manager.createSession("B", debuggerB);

        debuggerA.addBreakpoint(Breakpoint.build("f.xpl", 10));
        debuggerB.addBreakpoint(Breakpoint.build("g.xpl", 20));

        assertEquals(1, debuggerA.getBreakpoints().size());
        assertEquals(1, debuggerB.getBreakpoints().size());
        // A 的断点在 B 上不可见（按路径+行匹配互不命中）
        assertEquals(null, debuggerB.getBreakpointAt(SourceLocation.fromLine("f.xpl", 10, 0)));
    }
}
