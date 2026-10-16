/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.api.XLang;
import io.nop.xlang.debugger.CollectNotifier;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 路由外壳三条运行时路由单测（D3 §3.4）：无标记→默认会话/存活→会话执行器/已销毁→直通。
 * 用记录型执行器验证路由目标，不依赖真实调试器挂起。
 */
public class TestSessionRoutingExecutor {
    private SessionDebugManager manager;

    static class RecordingExecutor implements IExpressionExecutor {
        final AtomicInteger hits = new AtomicInteger();

        @Override
        public Object execute(IExecutableExpression expr, EvalRuntime rt) {
            hits.incrementAndGet();
            return "recording";
        }
    }

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

    private EvalRuntime newRt() {
        IEvalScope scope = XLang.newEvalScope();
        return new EvalRuntime(scope);
    }

    private IExecutableExpression anyExpr() {
        // 最小可执行表达式：直通形态下由 DefaultExpressionExecutor 自线程化执行
        return new IExecutableExpression() {
            @Override
            public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
                return "expr-result";
            }

            @Override
            public SourceLocation getLocation() {
                return null;
            }

            @Override
            public boolean allowBreakPoint() {
                return false;
            }

            @Override
            public void visit(io.nop.core.lang.eval.IExecutableExpressionVisitor visitor) {
                // 测试桩：无子节点
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
                sb.append("test-expr");
            }
        };
    }

    @Test
    public void testNoMarkerRoutesToDefaultSession() {
        SessionRoutingExecutor router = new SessionRoutingExecutor(manager);
        // 默认会话执行器 = DebugExpressionExecutor(默认 debugger)；无断点时直通返回表达式结果
        assertEquals("expr-result", router.execute(anyExpr(), newRt()));
    }

    @Test
    public void testAliveMarkerRoutesToSessionExecutor() {
        RecordingExecutor recording = new RecordingExecutor();
        DebugSessionHandle handle = handleWith(recording);
        SessionRoutingExecutor router = new SessionRoutingExecutor(manager);

        Object result = DebugSessionScope.withSession(handle, () -> router.execute(anyExpr(), newRt()));

        assertEquals("recording", result);
        assertEquals(1, recording.hits.get());
    }

    @Test
    public void testDestroyedMarkerRoutesToDestroyedExecutor() {
        RecordingExecutor recording = new RecordingExecutor();
        DebugSessionHandle handle = handleWith(recording);
        manager.destroySession(handle);
        // destroyed 哨兵的执行器是 DestroyedSessionExecutor（委托默认执行器），不会命中会话执行器
        SessionRoutingExecutor router = new SessionRoutingExecutor(manager);

        Object result = DebugSessionScope.withSession(handle, () -> router.execute(anyExpr(), newRt()));

        assertEquals("expr-result", result);
        assertEquals(0, recording.hits.get());
    }

    @Test
    public void testDestroyedExecutorDelegatesAndChecksChannel() {
        AtomicInteger channelChecks = new AtomicInteger();
        DestroyedSessionExecutor executor = new DestroyedSessionExecutor(DefaultExpressionExecutor.INSTANCE,
                rt -> channelChecks.incrementAndGet());

        assertEquals("expr-result", executor.execute(anyExpr(), newRt()));
        assertEquals(1, channelChecks.get());
    }

    private DebugSessionHandle handleWith(IExpressionExecutor executor) {
        // 反射不可取：借助 createSession 后替换不可行——直接构造同包可见句柄
        return new DebugSessionHandle("rec-" + System.nanoTime(), executor);
    }

    /** 异常事件外壳四元组捕获桩（item 13）。 */
    static class CapturingNotifier extends CollectNotifier {
        final List<Object[]> exceptions = new ArrayList<>();

        @Override
        public void notifyException(String sourcePath, int line, String message, String errorCode) {
            exceptions.add(new Object[]{sourcePath, line, message, errorCode});
        }
    }

    private IExecutableExpression throwingExpr(RuntimeException boom) {
        return new IExecutableExpression() {
            @Override
            public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
                throw boom;
            }

            @Override
            public SourceLocation getLocation() {
                return null;
            }

            @Override
            public boolean allowBreakPoint() {
                return false;
            }

            @Override
            public void visit(io.nop.core.lang.eval.IExecutableExpressionVisitor visitor) {
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
                sb.append("throwing-expr");
            }
        };
    }

    /** item 13：嵌套重入只报最外层一次 + rethrow 保原异常引用。 */
    @Test
    public void testExceptionReportedOnceAtOutermost() {
        XLangDebugger debugger = new XLangDebugger();
        CapturingNotifier notifier = new CapturingNotifier();
        debugger.setNotifier(notifier);
        DebugSessionHandle handle = manager.createSession("exc-nested", debugger);
        SessionRoutingExecutor router = new SessionRoutingExecutor(manager);

        RuntimeException boom = new RuntimeException("boom-inner");
        IExecutableExpression inner = throwingExpr(boom);
        // 模拟函数调用边界重读全局槽位：嵌套 execute 经同一路由外壳再次进入
        IExecutableExpression outer = new IExecutableExpression() {
            @Override
            public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
                return router.execute(inner, rt);
            }

            @Override
            public SourceLocation getLocation() {
                return null;
            }

            @Override
            public boolean allowBreakPoint() {
                return false;
            }

            @Override
            public void visit(io.nop.core.lang.eval.IExecutableExpressionVisitor visitor) {
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
                sb.append("outer-expr");
            }
        };

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> DebugSessionScope.withSession(handle, () -> router.execute(outer, newRt())));
        assertSame(boom, thrown, "异常传播行为零改变（原异常引用）");
        assertEquals(1, notifier.exceptions.size(), "嵌套重入只报最外层一次");
        // 非 NopException：message = toString（D1 §3.5 无 errorCode 承载）
        assertEquals("java.lang.RuntimeException: boom-inner", notifier.exceptions.get(0)[2]);
        assertNull(notifier.exceptions.get(0)[3]);
    }

    /** item 13：NopException 定位与 errorCode 承载（line>0 才发射定位）。 */
    @Test
    public void testNopExceptionLocationExtracted() {
        XLangDebugger debugger = new XLangDebugger();
        CapturingNotifier notifier = new CapturingNotifier();
        debugger.setNotifier(notifier);
        DebugSessionHandle handle = manager.createSession("exc-loc", debugger);
        SessionRoutingExecutor router = new SessionRoutingExecutor(manager);

        NopException boom = new NopException(XLangErrors.ERR_XLANG_DEBUGGER_ALREADY_CLOSED)
                .loc(SourceLocation.fromLine("a.xpl", 7));

        assertThrows(NopException.class,
                () -> DebugSessionScope.withSession(handle, () -> router.execute(throwingExpr(boom), newRt())));
        assertEquals(1, notifier.exceptions.size());
        Object[] reported = notifier.exceptions.get(0);
        assertEquals("a.xpl", reported[0]);
        assertEquals(7, reported[1]);
        assertEquals("nop.err.xlang.debugger-already-closed", reported[3]);
    }

    /** item 13：default 分支（无标记）经 default 会话 notifier 上报。 */
    @Test
    public void testDefaultSessionExceptionReported() {
        XLangDebugger defaultDebugger = manager.getSession("default").getDebugger();
        assertNotNull(defaultDebugger);
        CapturingNotifier notifier = new CapturingNotifier();
        defaultDebugger.setNotifier(notifier);
        SessionRoutingExecutor router = new SessionRoutingExecutor(manager);

        RuntimeException boom = new RuntimeException("boom-default");
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> router.execute(throwingExpr(boom), newRt()));
        assertSame(boom, thrown);
        assertEquals(1, notifier.exceptions.size(), "default 会话兜底路径同样上报");
    }

    /** item 13：destroyed 分支包报（真实 debugger notifier 可达）；哨兵句柄（debugger=null）null 守卫不抛错。 */
    @Test
    public void testDestroyedBranchReportAndNullGuard() {
        XLangDebugger debugger = new XLangDebugger();
        CapturingNotifier notifier = new CapturingNotifier();
        debugger.setNotifier(notifier);
        DebugSessionHandle handle = manager.createSession("exc-destroyed", debugger);
        manager.destroySession(handle);
        SessionRoutingExecutor router = new SessionRoutingExecutor(manager);

        RuntimeException boom = new RuntimeException("boom-destroyed");
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> DebugSessionScope.withSession(handle, () -> router.execute(throwingExpr(boom), newRt())));
        assertSame(boom, thrown);
        assertEquals(1, notifier.exceptions.size(), "destroyed 分支统一包报（发送层行为由 notifier 自决）");

        // 哨兵句柄（2 参构造，debugger=null）：null 守卫不抛错，异常原样传播
        DebugSessionHandle sentinel = handleWith(new RecordingExecutor());
        manager.destroySession(sentinel);
        RuntimeException boom2 = new RuntimeException("boom-sentinel");
        RuntimeException thrown2 = assertThrows(RuntimeException.class,
                () -> DebugSessionScope.withSession(sentinel, () -> router.execute(throwingExpr(boom2), newRt())));
        assertSame(boom2, thrown2, "null debugger 守卫跳过上报，不影响传播");
    }
}
