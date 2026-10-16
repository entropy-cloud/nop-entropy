/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.api.XLang;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成验证（Anti-Hollow + Wiring Verification）：从 XLang.execute 入口经路由外壳到
 * 会话实例 checkBreakpoint 的运行时连通性——含函数调用边界重取全局执行器后的路由不变
 * （D3 §3.2 双机制论证的运行时验证）。
 *
 * <p>采用 recording proxy（子类覆写 checkBreakpoint 计数，免挂起/resume 编排）。
 * CoreInitialization bootstrap 后必须在 @AfterAll 恢复全局 executor 槽位，防 fork 污染。
 */
public class TestSessionRoutingIntegration {
    static class RecordingDebugger extends XLangDebugger {
        final AtomicInteger checkHits = new AtomicInteger();

        @Override
        public void checkBreakpoint(io.nop.api.core.util.SourceLocation loc, io.nop.core.lang.eval.EvalRuntime rt) {
            checkHits.incrementAndGet();
        }
    }

    static SessionDebugManager manager;
    static RecordingDebugger sessionDebugger;
    static IExpressionExecutor prevExecutor;

    @BeforeAll
    static void beforeAll() {
        CoreInitialization.initialize();
        manager = SessionDebugManager.instance();
        sessionDebugger = new RecordingDebugger();
        manager.init(new XLangDebugger());
        manager.createSession("s-int", sessionDebugger);
        prevExecutor = EvalExprProvider.getGlobalExecutor();
        EvalExprProvider.registerGlobalExecutor(new SessionRoutingExecutor(manager));
    }

    @AfterAll
    static void afterAll() {
        EvalExprProvider.registerGlobalExecutor(DefaultExpressionExecutor.INSTANCE);
        if (prevExecutor != null && !(prevExecutor instanceof DefaultExpressionExecutor))
            EvalExprProvider.registerGlobalExecutor(prevExecutor);
        DebugSessionScope.clear();
        manager.reset();
        CoreInitialization.destroy();
    }

    private static int runInSession(String sessionId, String script) {
        return DebugSessionScope.withSession(sessionId, () -> {
            // compile 在标记窗口内（D3：窗口覆盖 compile+execute）；invoke 经 XLang.execute
            // choke point → 全局执行器（路由外壳）→ 函数调用边界重取全局执行器仍路由回会话
            Object result = XLang.newCompileTool()
                    .compileFullExpr(null, script)
                    .invoke(XLang.newEvalScope());
            return result instanceof Number ? ((Number) result).intValue() : -1;
        });
    }

    @Test
    public void testFunctionCallBoundaryRoutingReachesSessionDebugger() {
        int hitsBefore = sessionDebugger.checkHits.get();
        // 脚本含函数定义 + 函数调用：调用边界 ExecutableFunction.invoke 重读全局槽位，
        // 拿到的仍是路由外壳单实例——同线程标记下路由回本会话
        int result = runInSession("s-int", "function inc(v) { return v + 1; } return inc(41);");
        assertEquals(42, result);
        assertTrue(sessionDebugger.checkHits.get() > hitsBefore,
                "checkBreakpoint 必须到达会话实例（边界重取后路由不变）");
    }

    @Test
    public void testDifferentSessionsRouteToOwnDebugger() {
        RecordingDebugger otherDebugger = new RecordingDebugger();
        manager.createSession("s-other", otherDebugger);

        int otherBefore = otherDebugger.checkHits.get();
        int sessionBefore = sessionDebugger.checkHits.get();

        int r1 = runInSession("s-other", "function f() { return 7; } return f();");
        assertEquals(7, r1);
        assertTrue(otherDebugger.checkHits.get() > otherBefore, "路由必须命中 s-other 会话实例");
        // r1（s-other）不得泄漏进 s-int 会话——断言在 r2 之前（r2 本身命中 s-int 属预期）
        assertEquals(sessionBefore, sessionDebugger.checkHits.get(), "s-other 的执行不得泄漏进 s-int 会话");

        int r2 = runInSession("s-int", "function g() { return 8; } return g();");
        assertEquals(8, r2);
        assertTrue(sessionDebugger.checkHits.get() > sessionBefore, "r2 必须命中 s-int 会话实例");
    }

    @Test
    public void testNoMarkerDoesNotHitWebSession() {
        RecordingDebugger otherDebugger = new RecordingDebugger();
        manager.createSession("s-nomark", otherDebugger);
        int otherBefore = otherDebugger.checkHits.get();

        // 无标记执行（编译期求值等场景）：走默认会话，不进任何 web 会话
        Object value = XLang.newCompileTool().compileFullExpr(null, "1 + 1").invoke(XLang.newEvalScope());

        assertEquals(2, value);
        assertEquals(otherBefore, otherDebugger.checkHits.get(), "无标记执行不得进入 web 会话");
    }
}
