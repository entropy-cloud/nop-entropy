/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.Breakpoint;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.debugger.CollectNotifier;
import io.nop.xlang.debugger.ManualClock;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static io.nop.api.core.util.SourceLocation.fromLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REPL 取消通道 + 融合执行器测试（plan 2473 Phase 1，D6 §3.2/§3.3）：
 * 死循环 deadline 打断（WhileExecutable 回边每轮进融合执行器入口）、Error 系穿透
 * 脚本 catch(Exception)、跨线程 cancel、无 deadline 零影响、destroyed 分支取消保护、
 * REPL 代码可断点（融合执行器双能力）。
 *
 * <p>编排：SessionRoutingExecutor 注册为全局执行器 + DebugSessionScope 标记窗口
 * （照 TestSessionRoutingIntegration 模式）——compile+invoke 全程经融合执行器。
 */
public class TestReplDeadline {
    /** 远大于编译期入口检查次数：读数到此说明工作线程已在 while 回边上反复检查 deadline。 */
    private static final long LOOP_READS = 10_000;

    private SessionDebugManager manager;
    private io.nop.core.lang.eval.IExpressionExecutor prevExecutor;
    private ManualClock clock;

    @BeforeEach
    public void setUp() {
        clock = ManualClock.startingNow();
        CoreMetrics.registerClock(clock);
        manager = SessionDebugManager.instance();
        manager.init(new XLangDebugger());
        prevExecutor = io.nop.core.lang.eval.EvalExprProvider.getGlobalExecutor();
        io.nop.core.lang.eval.EvalExprProvider
                .registerGlobalExecutor(new SessionRoutingExecutor(manager));
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalReplDeadlineChannel.instance().end();
        io.nop.core.lang.eval.EvalExprProvider.registerGlobalExecutor(prevExecutor);
        manager.setSessionListener(null);
        DebugSessionScope.clear();
        manager.reset();
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    /** 每个融合执行器入口的 deadline 检查在工作线程上读一次时钟；等读数达到 reads。 */
    private void awaitClockReads(Thread worker, long reads) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (clock.readsBy(worker) < reads) {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("工作线程未在 10s 内读满 " + reads + " 次时钟");
            Thread.sleep(10);
        }
    }

    private static Object runScript(DebugSessionHandle handle, String script) {
        // 标记窗口覆盖 compile+execute（D3 插入点②）：编译期求值同样路由会话执行器
        return DebugSessionScope.withSession(handle, () -> {
            ExprEvalAction action = XLang.newCompileTool()
                    .compileFullExpr(fromLine("repl.xpl", 1), script);
            return action.invoke(XLang.newEvalScope());
        });
    }

    /** D6 §3.2 核心场景：while(true){} 死循环被 deadline 打断（Error 系穿透）。 */
    @Test
    public void testWhileTrueInterruptedByDeadline() throws Exception {
        DebugSessionHandle handle = manager.createSession("repl-1");
        AtomicReference<Throwable> error = new AtomicReference<>();
        ThreadLocalReplDeadlineChannel channel = ThreadLocalReplDeadlineChannel.instance();
        long deadline = clock.peekMillis() + 300;

        Thread worker = new Thread(() -> {
            channel.begin(deadline);
            try {
                runScript(handle, "while(true){}");
            } catch (Throwable t) {
                error.set(t);
            } finally {
                channel.end();
            }
        });
        worker.start();
        awaitClockReads(worker, LOOP_READS);
        clock.advance(300);
        worker.join(10000);
        assertFalse(worker.isAlive(), "死循环必须在 deadline 后被打断");
        ReplCancelledError cancelled = assertInstanceOf(ReplCancelledError.class, error.get());
        assertEquals("nop.err.xlang.debugger.repl-timeout", cancelled.getErrorCode());
    }

    /** 跨线程 cancel()：共享旗标翻转后执行线程下一入口抛 REPL_CANCELLED（评审 F3 语义）。 */
    @Test
    public void testCrossThreadCancelInterrupts() throws Exception {
        DebugSessionHandle handle = manager.createSession("repl-2");
        AtomicReference<Throwable> error = new AtomicReference<>();
        ThreadLocalReplDeadlineChannel channel = ThreadLocalReplDeadlineChannel.instance();
        long deadline = clock.peekMillis() + 60000;

        Thread worker = new Thread(() -> {
            channel.begin(deadline);
            try {
                runScript(handle, "while(true){}");
            } catch (Throwable t) {
                error.set(t);
            } finally {
                channel.end();
            }
        });
        worker.start();
        // 工作线程首个动作是 begin，有读数即 begin 已执行，此后的 cancel 不会被 begin 复位
        awaitClockReads(worker, 1);
        channel.cancel(); // 管理面跨线程取消
        worker.join(10000);
        assertFalse(worker.isAlive(), "cancel 后死循环应被打断");
        ReplCancelledError cancelled = assertInstanceOf(ReplCancelledError.class, error.get());
        assertEquals("nop.err.xlang.debugger.repl-cancelled", cancelled.getErrorCode());
    }

    /** Error 系裁定：脚本 catch(Exception) 不得吞掉取消异常（D6 §3.2 TryExecutable 反例面）。 */
    @Test
    public void testCancelErrorPiercesScriptCatch() throws Exception {
        DebugSessionHandle handle = manager.createSession("repl-3");
        AtomicReference<Throwable> error = new AtomicReference<>();
        ThreadLocalReplDeadlineChannel channel = ThreadLocalReplDeadlineChannel.instance();
        long deadline = clock.peekMillis() + 300;

        Thread worker = new Thread(() -> {
            channel.begin(deadline);
            try {
                runScript(handle, "try { while(true){} } catch(e) { return 'caught'; }");
            } catch (Throwable t) {
                error.set(t);
            } finally {
                channel.end();
            }
        });
        worker.start();
        awaitClockReads(worker, LOOP_READS);
        clock.advance(300);
        worker.join(10000);
        assertFalse(worker.isAlive());
        ReplCancelledError cancelled = assertInstanceOf(ReplCancelledError.class, error.get());
        assertEquals("nop.err.xlang.debugger.repl-timeout", cancelled.getErrorCode());
    }

    /** 无 deadline 零影响：通道已挂（融合执行器）但未 begin——脚本正常执行。 */
    @Test
    public void testNoDeadlineZeroEffect() {
        DebugSessionHandle handle = manager.createSession("repl-4");
        Object result = runScript(handle, "function f(v) { return v * 2; } return f(21);");
        assertEquals(42, result);
    }

    /** destroyed 分支取消保护（D6 §3.2 executeDestroyed）：销毁会话内的执行仍被 deadline 打断。 */
    @Test
    public void testDestroyedBranchStillCancels() throws Exception {
        DebugSessionHandle handle = manager.createSession("repl-5");
        manager.destroySession(handle);
        AtomicReference<Throwable> error = new AtomicReference<>();
        ThreadLocalReplDeadlineChannel channel = ThreadLocalReplDeadlineChannel.instance();
        long deadline = clock.peekMillis() + 300;

        Thread worker = new Thread(() -> {
            channel.begin(deadline);
            try {
                runScript(handle, "while(true){}");
            } catch (Throwable t) {
                error.set(t);
            } finally {
                channel.end();
            }
        });
        worker.start();
        awaitClockReads(worker, LOOP_READS);
        clock.advance(300);
        worker.join(10000);
        assertFalse(worker.isAlive(), "destroyed 分支不得解除取消保护");
        assertInstanceOf(ReplCancelledError.class, error.get());
    }

    /** 融合执行器双能力：REPL 代码可被会话断点命中（与编辑器断点一体，D6 §3.1 执行语义）。 */
    @Test
    public void testReplCodeHitsBreakpoint() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        CollectNotifier notifier = new CollectNotifier();
        debugger.setNotifier(notifier);
        DebugSessionHandle handle = manager.createSession("repl-6", debugger);
        // REPL 源的编译位置锚点 = repl.xpl:1（compileFullExpr 的 loc 参数）
        debugger.addBreakpoint(Breakpoint.build("repl.xpl", 1));

        AtomicReference<Object> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(runScript(handle, "return 6 * 7;")));
        worker.start();
        awaitSuspended(debugger);
        // REPL 命中断点挂起 → resume 放行 → 脚本执行完
        debugger.resume();
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertEquals(42, result.get());
    }

    private static void awaitSuspended(XLangDebugger debugger) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!debugger.isSuspended()) {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("REPL 代码未在 5s 内命中断点");
            Thread.sleep(10);
        }
    }
}
