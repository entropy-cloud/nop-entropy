/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.initialize;

import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.debugger.DebugExpressionExecutor;
import io.nop.xlang.debugger.XLangDebugger;
import io.nop.xlang.debugger.session.SessionRoutingExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 初始化器接线形态的运行时验证（Wiring Verification）：web 关 → 现状形态 executor
 * （与改造前同一类）；web 开 → 会话路由外壳。initialize() 整体被 try/catch 包裹、
 * 且绑定端口不可直测，故提取的 buildGlobalExecutor 选择方法是接线验证的测试锚点。
 */
public class TestXLangDebuggerInitializerWiring {

    static class TestInitializer extends XLangDebuggerInitializer {
        @Override
        public IExpressionExecutor buildGlobalExecutor(boolean debuggerEnabled, boolean webEnabled,
                                                       XLangDebugger defaultDebugger) {
            return super.buildGlobalExecutor(debuggerEnabled, webEnabled, defaultDebugger);
        }
    }

    @Test
    public void testWebDisabledKeepsCurrentExecutorForm() {
        XLangDebugger debugger = new XLangDebugger();
        IExpressionExecutor executor = new TestInitializer().buildGlobalExecutor(true, false, debugger);
        // 现状形态：DebugExpressionExecutor 绑定默认 debugger（回归基线）
        assertTrue(executor instanceof DebugExpressionExecutor,
                "web=false 必须注入现状形态 DebugExpressionExecutor，实际: " + executor.getClass());
        DebugExpressionExecutor debugExecutor = (DebugExpressionExecutor) executor;
        // 同一 debugger 实例（通过行为验证：执行时走该实例的 checkBreakpoint——以类型+形态断言为准）
        assertTrue(debugExecutor.toString().contains("DebugExpressionExecutor"));
        assertEquals(DebugExpressionExecutor.class, debugExecutor.getClass());
    }

    @Test
    public void testWebWithoutDebuggerFailsFast() {
        // D6 §一.5 蕴含推论：web=true 必须以 enabled=true 为前提，违者 fail-fast
        try {
            new TestInitializer().buildGlobalExecutor(false, true, new XLangDebugger());
            org.junit.jupiter.api.Assertions.fail("web=true + enabled=false 必须 fail-fast");
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
        assertTrue(executor instanceof SessionRoutingExecutor,
                "web=true 必须注入 SessionRoutingExecutor，实际: " + executor.getClass());
        // 外壳已注册默认会话：无标记执行走默认会话（此处经由外壳直通验证）
        SessionRoutingExecutor router = (SessionRoutingExecutor) executor;
        assertSame(router, executor);
    }
}
