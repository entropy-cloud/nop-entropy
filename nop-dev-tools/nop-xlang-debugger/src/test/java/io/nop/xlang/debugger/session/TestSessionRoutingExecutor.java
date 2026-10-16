/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.api.XLang;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
