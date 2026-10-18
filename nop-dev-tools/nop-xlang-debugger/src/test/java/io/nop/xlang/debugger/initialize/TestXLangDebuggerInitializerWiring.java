/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.initialize;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.api.XLang;
import io.nop.xlang.debugger.DebugExpressionExecutor;
import io.nop.xlang.debugger.XLangDebugger;
import io.nop.xlang.debugger.session.SessionRoutingExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 初始化器接线形态的运行时验证（Wiring Verification）：web 关 → 现状形态 executor
 * （与改造前同一类，且绑定注册到 SimpleRpcServer 的同一 debugger 实例——item 8 移交项②）；
 * web 开 → 会话路由外壳（无标记执行同样命中同一实例）。initialize() 整体被 try/catch 包裹、
 * 且绑定端口不可直测，故提取的 buildGlobalExecutor 选择方法是接线验证的测试锚点。
 */
public class TestXLangDebuggerInitializerWiring {

    static class RecordingDebugger extends XLangDebugger {
        final AtomicInteger checkHits = new AtomicInteger();

        @Override
        public void checkBreakpoint(SourceLocation loc, EvalRuntime rt) {
            checkHits.incrementAndGet();
        }
    }

    /** allowBreakPoint=true 的最小可执行表达式（触发 checkBreakpoint 路径）。 */
    static class BreakableExpr implements IExecutableExpression {
        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            return "expr-result";
        }

        @Override
        public SourceLocation getLocation() {
            return SourceLocation.fromLine("wiring.xpl", 1);
        }

        @Override
        public void visit(IExecutableExpressionVisitor visitor) {
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
            sb.append("wiring-expr");
        }
    }

    static class TestInitializer extends XLangDebuggerInitializer {
        @Override
        public IExpressionExecutor buildGlobalExecutor(boolean debuggerEnabled, boolean webEnabled,
                                                       XLangDebugger defaultDebugger) {
            return super.buildGlobalExecutor(debuggerEnabled, webEnabled, defaultDebugger);
        }
    }

    private static EvalRuntime newRt() {
        IEvalScope scope = XLang.newEvalScope();
        return new EvalRuntime(scope);
    }

    @Test
    public void testWebDisabledKeepsCurrentExecutorForm() {
        XLangDebugger debugger = new XLangDebugger();
        IExpressionExecutor executor = new TestInitializer().buildGlobalExecutor(true, false, debugger);
        // 现状形态：DebugExpressionExecutor（回归基线）
        assertEquals(DebugExpressionExecutor.class, executor.getClass());
    }

    @Test
    public void testWebDisabledExecutorBindsSameDebuggerInstance() {
        // item 8 移交项②：web=false 形态绑定注册到 SimpleRpcServer 的同一 debugger 实例
        RecordingDebugger debugger = new RecordingDebugger();
        IExpressionExecutor executor = new TestInitializer().buildGlobalExecutor(true, false, debugger);
        executor.execute(new BreakableExpr(), newRt());
        assertEquals(1, debugger.checkHits.get(),
                "web=false 形态必须绑定传入的同一 debugger 实例（checkBreakpoint 命中）");
    }

    @Test
    public void testWebEnabledNoMarkerAlsoBindsSameDebuggerInstance() {
        // item 8 移交项②：web=true 时无标记执行走默认会话，仍绑定同一 debugger 实例
        RecordingDebugger debugger = new RecordingDebugger();
        IExpressionExecutor executor = new TestInitializer().buildGlobalExecutor(true, true, debugger);
        executor.execute(new BreakableExpr(), newRt());
        assertEquals(1, debugger.checkHits.get(),
                "web=true 无标记执行必须命中默认会话（同一 debugger 实例）");
    }

    @Test
    public void testWebWithoutDebuggerFailsFast() {
        // D6 §一.5 蕴含推论：web=true 必须以 enabled=true 为前提，违者 fail-fast
        try {
            new TestInitializer().buildGlobalExecutor(false, true, new XLangDebugger());
            fail("web=true + enabled=false 必须 fail-fast");
        } catch (IllegalStateException expected) {
            assertTrue(expected.toString().contains("nop.xlang.debugger.enabled"));
        }
    }

    @Test
    public void testWebEnabledInstallsRoutingShell() {
        XLangDebugger debugger = new XLangDebugger();
        TestInitializer initializer = new TestInitializer();
        IExpressionExecutor executor = initializer.buildGlobalExecutor(true, true, debugger);
        // web 开：会话路由外壳
        assertSame((SessionRoutingExecutor) executor, executor);
    }
}
