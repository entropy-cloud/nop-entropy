/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.IBreakpointManager;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.api.core.util.SourceLocation.fromLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.40（6.5 单元）W-04/W-06 验收载体红③（C10-003 缺保护面，确定性形态）与红④（C10-004）：
 * 挂起交接 resume 纪元 + 宣告前快照（回调内同步投递 resume 不再挂死），以及 suspendedThreads
 * 清理周期化（S3 构造器注入缝 + 周期体绝缘）。红③ 驱动器 = 替身 notifier 在 notifyBreakAt 回调内
 * 同步投递命令（回调先于 doSuspend——确定性形态）；step 变体按 M1 修订形态：首停点回调内 resume
 * 使 lastSuspendThread 发布，自第 2 停点起回调内投递 stepOver。
 */
public class TestXLangDebuggerSuspendHandoff {

    /**
     * 替身 notifier（CollectNotifier 扩展，原替身零改动）：在回调内同步执行注入的动作
     * （resume/stepOver）——确定性构造「resume 先于 doSuspend 置位」的交接窗口。
     */
    static final class CallbackNotifier extends CollectNotifier {
        final AtomicLong callbackCount = new AtomicLong();
        private volatile Runnable onBreakAt;

        void setOnBreakAt(Runnable action) {
            this.onBreakAt = action;
        }

        @Override
        public void notifyBreakAt(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
            super.notifyBreakAt(thread, loc, rt);
            callbackCount.incrementAndGet();
            Runnable action = onBreakAt;
            if (action != null) {
                action.run();
            }
        }
    }

    private static EvalRuntime newRuntime() {
        IEvalScope scope = XLang.newEvalScope();
        EvalRuntime rt = new EvalRuntime(scope);
        rt.pushFrame(new EvalFrame(null, new String[]{"x", "y"}, new Object[]{"x1", "y1"}));
        return rt;
    }

    // 红③ 主驱动：回调内同步 resume，N=20 个普通断点行，断言线程完成 + 无幽灵挂起项
    @Test
    public void testResumeWithinBreakAtCallbackDoesNotHang() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        CallbackNotifier notifier = new CallbackNotifier();
        notifier.setOnBreakAt(debugger::resume);
        debugger.setNotifier(notifier);
        IBreakpointManager bpm = debugger;
        int totalLines = 20;
        for (int i = 1; i <= totalLines; i++) {
            bpm.addBreakpoint(Breakpoint.build("a", i));
        }

        AtomicLong threadId = new AtomicLong(-1);
        Thread debuggee = new Thread(() -> {
            threadId.set(Thread.currentThread().getId());
            EvalRuntime rt = newRuntime();
            for (int i = 1; i <= totalLines; i++) {
                debugger.checkBreakpoint(fromLine("a", i), rt);
            }
            notifier.getMessages().add("end");
        }, "cra-w04-resume-callback");
        debuggee.start();
        try {
            // 线程须在超时内走完全部停点（pre-fix：首停点回调内 resume 清标志后 doSuspend 重新
            // 置位并等待——线程挂死 join 超时，确定性红）
            debuggee.join(5000);
            assertFalse(debuggee.isAlive(), "debuggee must finish after resume within callback");

            List<String> drained = new ArrayList<>();
            notifier.getMessages().drainTo(drained);
            assertEquals(totalLines + 1, drained.size(),
                    "every breakpoint stop must fire exactly one callback plus the end marker: " + drained);
            assertEquals("end", drained.get(drained.size() - 1),
                    "debuggee must reach the end marker");
            assertEquals(totalLines, notifier.callbackCount.get(),
                    "every breakpoint stop must fire exactly one callback");

            // 终态无幽灵挂起项（getSuspendedThreads :223 读取点）
            assertTrue(debugger.getSuspendedThreads().isEmpty(),
                    "no ghost suspended entry may remain");
            assertFalse(debugger.isSuspended());
        } finally {
            // 收尾释放 pre-fix 挂死线程（post-fix 时为幂等收尾）
            debugger.close();
            debuggee.join(5000);
        }
    }

    // 红③ step 变体（M1 修订形态）：首停点回调内 resume（发布 lastSuspendThread），
    // 自第 2 停点起回调内投递 stepOver——stepOver() 不得被 null 守卫吞掉
    @Test
    public void testStepOverFromSecondStopCallbackAdvances() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        CallbackNotifier notifier = new CallbackNotifier();
        int[] stopIndex = {0};
        notifier.setOnBreakAt(() -> {
            stopIndex[0]++;
            if (stopIndex[0] == 1) {
                // 首停点：lastSuspendThread 尚未发布，回调内 stepOver 会被 null 守卫吞掉——投递 resume
                debugger.resume();
            } else {
                // 自第 2 停点起：lastSuspendThread 已发布，stepOver 经 monitorNotify 推进纪元
                debugger.stepOver();
            }
        });
        debugger.setNotifier(notifier);
        IBreakpointManager bpm = debugger;
        int totalLines = 20;
        for (int i = 1; i <= totalLines; i++) {
            bpm.addBreakpoint(Breakpoint.build("a", i));
        }

        Thread debuggee = new Thread(() -> {
            EvalRuntime rt = newRuntime();
            for (int i = 1; i <= totalLines; i++) {
                debugger.checkBreakpoint(fromLine("a", i), rt);
            }
            notifier.getMessages().add("end");
        }, "cra-w04-step-callback");
        debuggee.start();
        try {
            debuggee.join(5000);
            assertFalse(debuggee.isAlive(), "debuggee must finish under stepOver-from-callback");

            List<String> drained = new ArrayList<>();
            notifier.getMessages().drainTo(drained);
            assertEquals(totalLines + 1, drained.size(),
                    "every stop must fire one callback plus the end marker: " + drained);
            assertEquals("end", drained.get(drained.size() - 1), "debuggee must reach the end marker");
            assertEquals(totalLines, notifier.callbackCount.get(),
                    "every stop must fire one callback and advance by one command");
            assertTrue(stopIndex[0] >= 2, "stepOver must have been posted from the second stop on");
            assertTrue(debugger.getSuspendedThreads().isEmpty(), "no ghost suspended entry may remain");
        } finally {
            debugger.close();
            debuggee.join(5000);
        }
    }

    // 红④ W-06：S3 注入短周期；挂起跨越首个清理节拍（≥3 周期[m2]）后 resume 结束，
    // 死线程条目由后续周期清除（可区分「周期调度」与「可注入延迟的一次性调度」）
    @Test
    public void testPeriodicCleanupRemovesDeadThreadEntry() throws Exception {
        // 缝 S3：初始延迟/周期注入构造器（pre-fix 无此构造器——编译期构造红）
        XLangDebugger debugger = new XLangDebugger(50, 50);
        debugger.setUseExternalPath(false);
        CollectNotifier notifier = new CollectNotifier();
        debugger.setNotifier(notifier);
        IBreakpointManager bpm = debugger;
        bpm.addBreakpoint(Breakpoint.build("a", 1));

        AtomicLong threadId = new AtomicLong(-1);
        Thread debuggee = new Thread(() -> {
            threadId.set(Thread.currentThread().getId());
            EvalRuntime rt = newRuntime();
            debugger.checkBreakpoint(fromLine("a", 1), rt);
            debugger.checkBreakpoint(fromLine("a", 2), rt);
            notifier.getMessages().add("end");
        }, "cra-w06-cleanup");
        debuggee.start();

        // 等待挂起（break:[1:0:0:0]）
        assertEquals("break:[1:0:0:0]a", notifier.getMessages().poll(5, TimeUnit.SECONDS));
        long id = threadId.get();
        assertNotNull(debugger.getSuspendedThread(id), "entry must exist while suspended");

        // 保持挂起跨越首个清理节拍（≥3 周期 = 150ms）：一次性调度在首个节拍后不再触发，
        // 无法区分周期形态——挂起跨越节拍后再 resume 才能区分
        Thread.sleep(300);
        assertNotNull(debugger.getSuspendedThread(id),
                "entry must survive while the thread is alive (isAlive 判活零改动)");

        debugger.resume();
        debuggee.join(5000);
        assertFalse(debuggee.isAlive());
        assertEquals("end", notifier.getMessages().poll(5, TimeUnit.SECONDS));

        // 死线程条目须由后续清理周期清除（轮询 ≤5s）
        long deadline = System.currentTimeMillis() + 5000;
        while (debugger.getSuspendedThread(id) != null) {
            assertTrue(System.currentTimeMillis() < deadline,
                    "periodic cleanup must remove the dead thread entry within 5s");
            Thread.sleep(20);
        }
        assertNull(debugger.getSuspendedThread(id));

        debugger.close();
    }
}
