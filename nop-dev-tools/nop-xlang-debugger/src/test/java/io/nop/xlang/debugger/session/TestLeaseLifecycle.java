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
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.xlang.api.XLang;
import io.nop.xlang.debugger.IDebugNotifier;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.api.core.util.SourceLocation.fromLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租约机制测试组（plan 2469 九组，D2 §3.6 时序面）：
 * 续期保活/到期放行+事件互斥/close-放行竞态/releaseAll 宽限/授予判定①②/stepX 定向/
 * isSuspended 维护/双会话租约隔离/suspendOnce+mute 共存。
 *
 * <p>编排模式：后台线程直驱 checkBreakpoint（TestXLangDebugger 模式），主线程轮询挂起态。
 */
public class TestLeaseLifecycle {
    static class LeaseEventRecorder extends io.nop.xlang.debugger.CollectNotifier {
        final List<Long> leaseExpired = new ArrayList<>();
        final List<Long> resumed = new ArrayList<>();

        @Override
        public void notifyLeaseExpired(List<Long> threadIds) {
            leaseExpired.addAll(threadIds);
        }

        @Override
        public void notifyResumed(List<Long> threadIds, String cause) {
            resumed.addAll(threadIds);
        }
    }

    static class BreakableExpr implements IExecutableExpression {
        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            return "ok";
        }

        @Override
        public SourceLocation getLocation() {
            return fromLine("lease.xpl", 1);
        }

        @Override
        public void visit(IExecutableExpressionVisitor visitor) {
        }

        @Override
        public boolean containsReturnStatement() {
            return false;
        }

        @Override
        public boolean containsBreakStatement() {
            return false;
        }

        @Override
        public void display(StringBuilder sb) {
            sb.append("lease-expr");
        }
    }

    private static EvalRuntime newRt() {
        IEvalScope scope = XLang.newEvalScope();
        return new EvalRuntime(scope);
    }

    private static void awaitSuspended(XLangDebugger debugger) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!debugger.isSuspended()) {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("debugger 未在 5s 内挂起");
            Thread.sleep(10);
        }
    }

    private static void awaitFinished(Thread thread) throws InterruptedException {
        thread.join(5000);
        assertFalse(thread.isAlive(), "线程应在租约到期放行后执行完");
    }

    /** 挂起于断点的后台线程（无租约干扰：lease 禁用或已续期）。 */
    private static Thread startSuspendedHit(XLangDebugger debugger, Runnable onExit) {
        Thread thread = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
            } finally {
                if (onExit != null)
                    onExit.run();
            }
        });
        thread.start();
        return thread;
    }

    // ===== 组1：续期保活 =====
    @Test
    public void testRenewKeepsSuspensionAlive() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        LeaseEventRecorder recorder = new LeaseEventRecorder();
        debugger.setNotifier(recorder);
        debugger.addBreakpoint(Breakpoint.build("a", 1));

        debugger.renewLease(300);
        Thread thread = startSuspendedHit(debugger, null);
        awaitSuspended(debugger);

        // 若不续期，300ms 后到期放行；两次续期跨越 600ms 仍挂起 = 续期保活生效
        Thread.sleep(250);
        assertTrue(debugger.isSuspended(), "TTL 内应保持挂起");
        debugger.renewLease(300);
        Thread.sleep(250);
        assertTrue(debugger.isSuspended(), "续期后应继续保持挂起（原 deadline 已过）");

        debugger.resume();
        awaitFinished(thread);
        assertEquals(0, recorder.leaseExpired.size());
        assertEquals(1, recorder.resumed.size());
        debugger.close();
    }

    // ===== 组2：停止续期 → 到期放行且脚本执行完 + 事件互斥 =====
    @Test
    public void testLeaseExpiryReleasesThreadAndFiresEventOnce() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        LeaseEventRecorder recorder = new LeaseEventRecorder();
        debugger.setNotifier(recorder);
        debugger.addBreakpoint(Breakpoint.build("a", 1));

        debugger.renewLease(200);
        AtomicReference<Boolean> continued = new AtomicReference<>(false);
        Thread thread = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
                // 到期放行后脚本继续执行完（不抛错）——执行到这里即证明
                continued.set(true);
            } catch (Throwable t) {
                continued.set(false);
            }
        });
        thread.start();
        awaitSuspended(debugger);

        // 不再续期：TTL 200ms 到期 → 挂起线程正常返回
        awaitFinished(thread);
        assertTrue(continued.get(), "到期放行 = resume 语义：脚本必须继续执行完（不抛错）");
        assertEquals(1, recorder.leaseExpired.size(), "leaseExpired 恰一次（单线程单元素）");
        assertEquals(0, recorder.resumed.size(), "与 evt.resumed 互斥（用户未 resume）");
        assertFalse(debugger.isSuspended(), "放行后 isSuspended 复位");
        debugger.close();
    }

    // ===== 组3：close 与放行竞态 =====
    @Test
    public void testResumeThenCloseIsNormalReturn() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("a", 1));

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
                debugger.checkBreakpoint(fromLine("a", 2), newRt());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.start();
        awaitSuspended(debugger);
        debugger.resume(); // 用户放行
        debugger.close();  // 紧随 close（与放行竞态）
        awaitFinished(thread);
        assertEquals(null, error.get(), "放行后 close 不得按 close 语义中止（出口判定顺序：局部放行优先）");
    }

    @Test
    public void testStepReleaseThenCloseIsNormalReturn() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("a", 1));

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
                debugger.checkBreakpoint(fromLine("a", 2), newRt());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.start();
        awaitSuspended(debugger);
        debugger.stepInto(); // step 局部放行
        debugger.close();    // 紧随 close
        awaitFinished(thread);
        assertEquals(null, error.get(), "step 放行后 close 同样按放行优先处理");
    }

    @Test
    public void testCloseOnlyAbortsSuspendedThread() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("a", 1));

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.start();
        awaitSuspended(debugger);
        debugger.close(); // close 语义：中止挂起线程
        awaitFinished(thread);
        assertTrue(error.get() != null, "close 语义：挂起线程被中止抛 ALREADY_CLOSED");
    }

    // ===== 组4：releaseAll 宽限路径 =====
    @Test
    public void testReleaseAllGraceReleasesWithoutThrow() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        LeaseEventRecorder recorder = new LeaseEventRecorder();
        debugger.setNotifier(recorder);
        debugger.addBreakpoint(Breakpoint.build("a", 1));

        Thread thread = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
            } catch (Throwable t) {
                Assertions.fail("releaseAll 宽限放行不得抛错");
            }
        });
        thread.start();
        awaitSuspended(debugger);

        debugger.releaseAll(); // 宽限路径：聚合发 leaseExpired，不置 closed 不抛错
        awaitFinished(thread);
        assertFalse(debugger.isClosed(), "releaseAll 不置 closed");
        assertFalse(debugger.isSuspended());
        assertEquals(1, recorder.leaseExpired.size(), "宽限路径聚合发 leaseExpired");
        assertEquals(0, recorder.resumed.size());
        debugger.close();
    }

    // ===== 组5：授予判定①② =====
    @Test
    public void testClosedGrantSkipsSuspensionWithoutThrow() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        debugger.close(); // 授予判定①：closed

        Thread thread = new Thread(() -> {
            try {
                // 新命中：不挂起、不抛错、直接继续（D2 §3.1①）
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
            } catch (Throwable t) {
                Assertions.fail("授予判定①不得抛错: " + t);
            }
        });
        thread.start();
        awaitFinished(thread);
    }

    @Test
    public void testLeaseExpiredGrantSkipsSuspension() throws Exception {
        // 判定②钉住（审计 F1）：过期后命中断点 → 不挂起（线程从未进入挂起态 →
        // 零 leaseExpired 事件，对齐 D2 §3.1②）→ 不抛错直接继续
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        LeaseEventRecorder recorder = new LeaseEventRecorder();
        debugger.setNotifier(recorder);
        debugger.addBreakpoint(Breakpoint.build("a", 1));
        debugger.renewLease(100);
        Thread.sleep(200); // 租约过期

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.start();
        awaitFinished(thread);
        assertEquals(null, error.get(), "授予判定②不得抛错");
        assertEquals(0, recorder.leaseExpired.size(),
                "判定②跳过的命中从未进入挂起态：零 leaseExpired 事件（钉住判定②）");
        assertFalse(debugger.isSuspended());
    }

    // ===== 组6：stepX 定向 =====
    @Test
    public void testStepTargetsOnlyLastSuspendedThread() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("a", 1));
        debugger.addBreakpoint(Breakpoint.build("a", 2));

        AtomicReference<Boolean> aReleased = new AtomicReference<>(false);
        AtomicReference<Throwable> errorB = new AtomicReference<>();
        Thread threadA = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
                aReleased.set(true);
            } catch (Throwable t) {
                Assertions.fail("线程A异常: " + t);
            }
        });
        Thread threadB = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 2), newRt());
            } catch (Throwable t) {
                errorB.set(t);
            }
        });
        threadA.start();
        awaitSuspended(debugger); // A 挂起于 line1
        threadB.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (debugger.getSuspendedThreadIds().size() < 2) {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("线程B未挂起（双挂起未达成）");
            Thread.sleep(10);
        }
        // 双线程挂起：lastSuspendThread = B（最近挂起）；stepInto 仅释放 B
        debugger.stepInto();
        threadB.join(5000);
        assertFalse(threadB.isAlive(), "stepInto 必须定向释放 B（目标线程）");
        assertFalse(aReleased.get(), "stepInto 不得释放 A（非目标线程）");
        assertTrue(debugger.isSuspended(), "A 仍挂起");

        debugger.resume(); // 释放 A
        threadA.join(5000);
        assertTrue(aReleased.get());
        assertEquals(null, errorB.get());
    }

    @Test
    public void testStepWithoutSuspendedTargetThrowsModuleError() {
        XLangDebugger debugger = new XLangDebugger();
        org.junit.jupiter.api.Assertions.assertThrows(io.nop.api.core.exceptions.NopException.class,
                () -> debugger.stepInto(),
                "无挂起目标必须抛模块内 thread-not-suspended 错误码");
    }

    // ===== 组7：isSuspended 维护 + releaseThread 幂等 =====
    @Test
    public void testIsSuspendedMaintainedAfterTargetedRelease() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("a", 1));

        Thread thread = startSuspendedHit(debugger, null);
        awaitSuspended(debugger);
        assertTrue(debugger.isSuspended());

        long threadId = debugger.getSuspendedThreadIds().get(0);
        debugger.releaseThread(threadId); // 定向唤醒原语
        awaitFinished(thread);
        assertFalse(debugger.isSuspended(), "全部放行后 isSuspended 必须复位");

        // releaseThread 幂等：对不存在/已放行 threadId no-op
        debugger.releaseThread(threadId);
        debugger.releaseThread(999999);
    }

    // ===== 组8：双会话租约隔离 =====
    @Test
    public void testDualSessionLeaseIsolation() throws Exception {
        XLangDebugger debuggerA = new XLangDebugger();
        XLangDebugger debuggerB = new XLangDebugger();
        debuggerA.setUseExternalPath(false);
        debuggerB.setUseExternalPath(false);
        debuggerA.addBreakpoint(Breakpoint.build("a", 1));
        debuggerB.addBreakpoint(Breakpoint.build("b", 1));

        // A 短租约不再续期；B 长租约持续挂起
        debuggerA.renewLease(200);
        debuggerB.renewLease(60000);

        Thread threadA = new Thread(() -> {
            debuggerA.checkBreakpoint(fromLine("a", 1), newRt());
        });
        Thread threadB = new Thread(() -> {
            debuggerB.checkBreakpoint(fromLine("b", 1), newRt());
        });
        threadA.start();
        threadB.start();
        awaitSuspended(debuggerB);
        awaitFinished(threadA);

        assertTrue(debuggerB.isSuspended(), "B 的挂起不受 A 租约到期影响（会话租约隔离）");
        debuggerB.close();
    }

    // ===== 组9：suspendOnce + mute 共存 + 快照 =====
    @Test
    public void testSuspendOnceOnlySuspendsOneOfTwoThreads() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        debugger.suspend(); // 会话级一次生效旗标

        java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(2);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Runnable hit = () -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                settled.countDown();
            }
        };
        Thread t1 = new Thread(hit);
        Thread t2 = new Thread(hit);
        t1.start();
        t2.start();
        // 恰一个线程挂起（CAS 原子清除），另一个直接通过
        long deadline = System.currentTimeMillis() + 5000;
        while (debugger.getSuspendedThreadIds().size() != 1) {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("suspendOnce 应恰好挂起一个线程");
            Thread.sleep(10);
        }
        // 另一线程应已通过并结束
        t2.join(100);
        t1.join(100);
        assertTrue(debugger.isSuspended());
        debugger.close(); // 清理挂起线程
        assertEquals(null, error.get());
    }

    @Test
    public void testMuteCoexistsWithLease() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        debugger.renewLease(60000);
        debugger.muteBreakpoints(true); // 授予判定①②先于 mute guard：租约激活不影响 mute 语义

        Thread thread = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
            } catch (Throwable t) {
                Assertions.fail("mute 下不得挂起: " + t);
            }
        });
        thread.start();
        awaitFinished(thread);
    }

    @Test
    public void testLeaseDeadlineSnapshotAndDisabledDefault() {
        XLangDebugger debugger = new XLangDebugger();
        assertEquals(0, debugger.getLeaseDeadlineMillis(), "默认租约禁用（deadline=0）");
        debugger.renewLease(5000);
        assertTrue(debugger.getLeaseDeadlineMillis() > System.currentTimeMillis() - 100,
                "renew 后 deadline 为未来时刻快照");
    }
}
