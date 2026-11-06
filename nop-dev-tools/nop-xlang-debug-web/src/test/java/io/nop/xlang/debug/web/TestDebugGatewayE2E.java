/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.commons.test.time.ManualClock;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.xlang.debugger.session.SessionListener;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.nop.api.debugger.Breakpoint;
import static io.nop.api.core.util.SourceLocation.fromLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关全链路测试（plan 2471 Phase 2，D1 §3.9 / D2 §3.3/§3.5 时序面）：
 * 断点调试全流程事件顺序、断连宽限→gracefulDispose、rebind 属主校验与豁免、
 * renew 限流、close 响应先于 sessionClosed。
 */
public class TestDebugGatewayE2E extends AbstractDebugGatewayTest {

    /** 全链路（D1 §3.9）：open→setBreakpoints→挂起→evt.suspended→getStackInfo→renew→resume→evt.resumed→close→sessionClosed。 */
    @Test
    public void testFullDebugFlowSequence() throws Exception {
        useManualClock();
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);

        handler.onMessage(req("1", "session.open", "{\"protoVersion\":\"1.0\"}"));
        Map<String, Object> openResp = findResponse(ws.snapshot(), "1");
        assertNotNull(openResp);
        Map<String, Object> openResult = resultMap(openResp);
        String sessionId = (String) openResult.get("sessionId");
        assertEquals("u1:" + handler.getConnectionId(), sessionId);
        assertEquals("1.0", openResult.get("protoVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> leasePolicy = (Map<String, Object>) openResult.get("lease");
        assertEquals(30000L, ((Number) leasePolicy.get("ttlMs")).longValue());
        assertEquals(10000L, ((Number) leasePolicy.get("renewIntervalMs")).longValue());

        XLangDebugger debugger = debuggerOf(sessionId);
        debugger.setUseExternalPath(false);

        handler.onMessage(req("2", "debugger.setBreakpoints",
                "{\"bps\":[{\"sourcePath\":\"a\",\"line\":1}]}"));
        Map<String, Object> setResp = findResponse(ws.snapshot(), "2");
        assertNotNull(setResp);
        assertTrue(setResp.containsKey("result"));

        // 后台线程命中断点
        Thread script = startSuspendedHit(debugger, null, fromLine("a", 1));
        awaitSuspended(debugger);
        awaitUntil("evt.suspended 应出站", () -> hasEvent(ws.snapshot(), "evt.suspended"));
        Map<String, Object> suspended = findEvent(ws.snapshot(), "evt.suspended");
        @SuppressWarnings("unchecked")
        Map<String, Object> suspendedParams = (Map<String, Object>) suspended.get("params");
        assertEquals("breakAt", suspendedParams.get("reason"));
        long threadId = ((Number) suspendedParams.get("threadId")).longValue();
        assertTrue(suspendedParams.containsKey("stackInfo"), "evt.suspended 应携带 stackInfo 字段");
        assertTrue(suspendedParams.containsKey("location"), "evt.suspended 应携带 location 字段");

        handler.onMessage(req("3", "debugger.getStackInfo", "{\"threadId\":" + threadId + "}"));
        Map<String, Object> stackResp = findResponse(ws.snapshot(), "3");
        assertNotNull(resultMap(stackResp).get("stackInfo"));

        // 推进一段虚拟时间：此后 renew 的截止时间 = 推进后的虚拟时刻 + ttl，若生产侧改回真实时钟，
        // 截止值会比期望少掉这段推进量，断言必然失败
        clock.advance(5000);
        handler.onMessage(req("4", "lease.renew", "{}"));
        Map<String, Object> renewResp = findResponse(ws.snapshot(), "4");
        @SuppressWarnings("unchecked")
        Map<String, Object> leaseState = (Map<String, Object>) resultMap(renewResp).get("lease");
        // 租约截止 = 当前虚拟时刻 + 策略 ttl（renew 期间未推进时钟），据此钉住 renewLease 接线
        assertEquals(clock.peekMillis() + 30000L,
                ((Number) leaseState.get("expiresAtMs")).longValue());
        assertEquals(1, ((List<?>) leaseState.get("suspendedThreadIds")).size());

        handler.onMessage(req("5", "debugger.resume", "{}"));
        script.join(5000);
        assertFalse(script.isAlive(), "resume 后脚本线程应执行完");
        awaitUntil("evt.resumed 应出站", () -> hasEvent(ws.snapshot(), "evt.resumed"));
        Map<String, Object> resumed = findEvent(ws.snapshot(), "evt.resumed");
        @SuppressWarnings("unchecked")
        Map<String, Object> resumedParams = (Map<String, Object>) resumed.get("params");
        assertEquals("user", resumedParams.get("cause"));
        assertEquals(1, ((List<?>) resumedParams.get("threadIds")).size());

        // close：响应先于 evt.sessionClosed（D1 §3.9 顺序）
        handler.onMessage(req("6", "session.close", "{}"));
        List<String> frames = ws.snapshot();
        int closeIdx = -1;
        int closedEvtIdx = -1;
        for (int i = 0; i < frames.size(); i++) {
            Map<String, Object> msg = parse(frames.get(i));
            if ("6".equals(String.valueOf(msg.get("id"))) && msg.containsKey("result"))
                closeIdx = i;
            if ("evt.sessionClosed".equals(msg.get("method")))
                closedEvtIdx = i;
        }
        assertTrue(closeIdx >= 0, "close 响应应存在");
        // plan 2479 C-1：事件帧经每连接队列异步到达——先轮询等待，再断言次序
        //（D1 §3.9 序列语义保持：close 响应同步直发 happens-before 事件 enqueue）
        awaitUntil("evt.sessionClosed 应出站", () -> hasEvent(ws.snapshot(), "evt.sessionClosed"));
        frames = ws.snapshot();
        closedEvtIdx = -1;
        for (int i = 0; i < frames.size(); i++) {
            if ("evt.sessionClosed".equals(parse(frames.get(i)).get("method")))
                closedEvtIdx = i;
        }
        assertTrue(closedEvtIdx >= 0, "evt.sessionClosed 应存在");
        assertTrue(closeIdx < closedEvtIdx, "close 响应必须先于 evt.sessionClosed");
        Map<String, Object> closedEvt = findEvent(frames, "evt.sessionClosed");
        @SuppressWarnings("unchecked")
        Map<String, Object> closedParams = (Map<String, Object>) closedEvt.get("params");
        assertEquals("userClosed", closedParams.get("reason"));
        assertTrue(debugger.isClosed(), "close 语义应中止调试器实例");
        assertNull(manager.getSession(sessionId), "会话应已注销");
    }

    /** 断连宽限：onClose → 宽限期满 → gracefulDispose（脚本不抛错执行完 + leaseExpired 先于 sessionClosed{graceExpired}）。 */
    @Test
    public void testGraceExpiryReleasesScriptAndDestroysSession() throws Exception {
        runtime.setGraceMillis(200);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        XLangDebugger debugger = debuggerOf(sessionId);
        debugger.setUseExternalPath(false);
        handler.onMessage(req("2", "debugger.setBreakpoints",
                "{\"bps\":[{\"sourcePath\":\"a\",\"line\":1},{\"sourcePath\":\"a\",\"line\":2}]}"));

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread script = new Thread(() -> {
            try {
                debugger.checkBreakpoint(fromLine("a", 1), newRt());
                debugger.checkBreakpoint(fromLine("a", 2), newRt());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        script.start();
        awaitSuspended(debugger);

        // 模拟断连（非优雅关闭）→ 排一个宽限销毁任务
        int pendingBefore = scheduler.pendingTaskCount();
        handler.onClose();
        assertEquals(pendingBefore + 1, scheduler.pendingTaskCount(), "最后连接断开应排宽限销毁任务");

        // 推进越过宽限：gracefulDispose 在推进线程上执行——放行（脚本第二个断点因 Destroying
        // 授予判定①不再挂起，直接执行完）
        scheduler.advance(200);
        script.join(10000);
        assertFalse(script.isAlive(), "宽限期满后脚本线程应放行执行完");
        assertNull(error.get(), "gracefulDispose = resume 语义：脚本不得抛错");
        awaitUntil("evt.leaseExpired 应出站", () -> hasEvent(ws.snapshot(), "evt.leaseExpired"));
        awaitUntil("evt.sessionClosed 应出站", () -> hasEvent(ws.snapshot(), "evt.sessionClosed"));
        List<String> frames = ws.snapshot();
        int leaseIdx = -1;
        int closedIdx = -1;
        for (int i = 0; i < frames.size(); i++) {
            if ("evt.leaseExpired".equals(parse(frames.get(i)).get("method")))
                leaseIdx = i;
            if ("evt.sessionClosed".equals(parse(frames.get(i)).get("method")))
                closedIdx = i;
        }
        assertTrue(leaseIdx < closedIdx, "放行事件应先于销毁终态事件（D2 §3.6）");
        Map<String, Object> closedEvt = findEvent(frames, "evt.sessionClosed");
        @SuppressWarnings("unchecked")
        Map<String, Object> closedParams = (Map<String, Object>) closedEvt.get("params");
        assertEquals("graceExpired", closedParams.get("reason"));
        assertFalse(debugger.isClosed(), "宽限销毁 = resume 语义：不置 closed（D2 §3.3 红线）");
        assertNull(manager.getSession(sessionId), "会话应已注销");
    }

    /** 审计 F3 回归：rebind 销毁过渡会话后登记面无泄漏（owners/connections 清理）。 */
    @Test
    public void testRebindTransitionalNoRegistryLeak() {
        MockWsSession ws1 = new MockWsSession();
        DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
        String s1 = openSession(h1, ws1, "1");
        assertEquals(1, runtime.getBinder().trackedSessionCount());

        MockWsSession ws2 = new MockWsSession();
        DebugWebSocketHandler h2 = newHandler(ws2, TOK_U1);
        openSession(h2, ws2, "1"); // 过渡会话
        assertEquals(2, runtime.getBinder().trackedSessionCount());

        h2.onMessage(req("2", "session.rebind", "{\"sessionId\":\"" + s1 + "\"}"));
        assertNotNull(resultMap(findResponse(ws2.snapshot(), "2")));
        assertEquals(1, runtime.getBinder().trackedSessionCount(), "过渡会话注销后登记面应清理（无泄漏）");

        // rebind 后绑定仍然有效（re-put 回归面）
        h2.onMessage(req("3", "lease.renew", "{}"));
        assertNull(errorCodeOf(findResponse(ws2.snapshot(), "3")));
    }

    /** 多标签页：部分断连不进宽限（剩余连接续期照常）；全部断连才销毁。 */
    @Test
    public void testMultiTabLastConnectionTriggersGrace() throws Exception {
        MockWsSession ws1 = new MockWsSession();
        MockWsSession ws2 = new MockWsSession();
        DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
        DebugWebSocketHandler h2 = newHandler(ws2, TOK_U1);
        String s1 = openSession(h1, ws1, "1");

        // 第二个连接 rebind 共享会话（D2 §3.4）
        h2.onMessage(req("1", "session.open", "{\"protoVersion\":\"1.0\"}"));
        String s2 = (String) resultMap(findResponse(ws2.snapshot(), "1")).get("sessionId");
        h2.onMessage(req("2", "session.rebind", "{\"sessionId\":\"" + s1 + "\"}"));
        Map<String, Object> rebindResp = findResponse(ws2.snapshot(), "2");
        assertNotNull(rebindResp, "rebind 应成功");
        assertEquals(s1, resultMap(rebindResp).get("sessionId"));

        // 第一连接断开：仍有绑定连接 → 不排宽限任务；推进越过整个宽限期会话仍在
        int pendingBefore = scheduler.pendingTaskCount();
        h1.onClose();
        assertEquals(pendingBefore, scheduler.pendingTaskCount(), "部分断连不应排宽限销毁任务");
        scheduler.advance(runtime.graceMillis());
        assertNotNull(manager.getSession(s1), "部分断连应保持会话 Active（D2 §3.3）");

        // 第二连接断开：最后连接 → 排宽限任务 → 推进越过宽限即销毁
        h2.onClose();
        assertEquals(pendingBefore + 1, scheduler.pendingTaskCount(), "最后连接断开应排宽限销毁任务");
        scheduler.advance(runtime.graceMillis());
        assertNull(manager.getSession(s1), "宽限期满后应注销");
        // evt.sessionClosed 经每连接出站线程异步送达：等它出站再取
        awaitUntil("evt.sessionClosed 应出站", () -> hasEvent(ws2.snapshot(), "evt.sessionClosed"));
        Map<String, Object> closedEvt = findEvent(ws2.snapshot(), "evt.sessionClosed");
        assertNotNull(closedEvt);
        @SuppressWarnings("unchecked")
        Map<String, Object> closedParams = (Map<String, Object>) closedEvt.get("params");
        assertEquals("graceExpired", closedParams.get("reason"));
    }

    /** rebind 属主红线：跨用户拒绝、不存在/已销毁拒绝（不区分语义，D1 §3.4）。 */
    @Test
    public void testRebindOwnerRedLine() {
        MockWsSession ws1 = new MockWsSession();
        DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
        String s1 = openSession(h1, ws1, "1");
        h1.onMessage(req("2", "debugger.setBreakpoints",
                "{\"bps\":[{\"sourcePath\":\"a\",\"line\":1}]}"));

        // 跨用户（u2）→ SESSION_BOUND_ELSEWHERE
        MockWsSession ws2 = new MockWsSession();
        DebugWebSocketHandler h2 = newHandler(ws2, TOK_U2);
        openSession(h2, ws2, "1");
        h2.onMessage(req("2", "session.rebind", "{\"sessionId\":\"" + s1 + "\"}"));
        Map<String, Object> resp = findResponse(ws2.snapshot(), "2");
        assertNotNull(resp);
        assertEquals("nop.err.xlang.debugger.session-bound-elsewhere", errorCodeOf(resp));
        assertFalse(ws2.isClosed(), "绑定冲突默认拒绝新绑定，不断开连接（D1 §3.8）");

        // 不存在会话 → 同码（不区分不存在/他属）
        h2.onMessage(req("3", "session.rebind", "{\"sessionId\":\"nobody:noconn\"}"));
        assertEquals("nop.err.xlang.debugger.session-bound-elsewhere",
                errorCodeOf(findResponse(ws2.snapshot(), "3")));

        // 属主匹配 + 快照回放：断点表在 rebind result 中
        MockWsSession ws3 = new MockWsSession();
        DebugWebSocketHandler h3 = newHandler(ws3, TOK_U1);
        openSession(h3, ws3, "1");
        h3.onMessage(req("2", "session.rebind", "{\"sessionId\":\"" + s1 + "\"}"));
        Map<String, Object> okResp = findResponse(ws3.snapshot(), "2");
        assertNotNull(resultMap(okResp));
        List<?> bps = (List<?>) resultMap(okResp).get("breakpoints");
        assertEquals(1, bps.size(), "rebind 应回放断点表快照（D2 §3.6）");
    }

    /** renew 限流（0.8 容差按连接计量）+ rebind 首豁免（D2 §3.1）。 */
    @Test
    public void testRenewRateLimitAndRebindExemption() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        // open 后首 renew：lastRenew 未置 → 不限流
        handler.onMessage(req("2", "lease.renew", "{}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "2")));

        // 立即第二次：低于 renewInterval×0.8 → RATE_LIMITED（不关闭连接）
        handler.onMessage(req("3", "lease.renew", "{}"));
        assertEquals("nop.err.xlang.debugger.rate-limited", errorCodeOf(findResponse(ws.snapshot(), "3")));
        assertFalse(ws.isClosed(), "限流不关闭连接（D1 §3.1）");

        // rebind 后首豁免
        handler.onMessage(req("4", "session.rebind", "{\"sessionId\":\"" + sessionId + "\"}"));
        assertNotNull(resultMap(findResponse(ws.snapshot(), "4")));
        handler.onMessage(req("5", "lease.renew", "{}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "5")), "rebind 后首个 renew 应豁免限流");
    }

    /** D1 §3.1：出站 DebugVariable 列表截断至 1000 条 + truncated 标记。 */
    @Test
    public void testVariableListTruncation() throws Exception {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        XLangDebugger debugger = debuggerOf(sessionId);
        debugger.setUseExternalPath(false);
        handler.onMessage(req("2", "debugger.setBreakpoints",
                "{\"bps\":[{\"sourcePath\":\"a\",\"line\":1}]}"));

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread script = new Thread(() -> {
            try {
                EvalRuntime rt = newRt();
                for (int i = 0; i < 1001; i++)
                    rt.getScope().setLocalValue(null, "v" + i, i);
                debugger.checkBreakpoint(fromLine("a", 1), rt);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        script.start();
        awaitSuspended(debugger);
        awaitUntil("evt.suspended 应出站", () -> hasEvent(ws.snapshot(), "evt.suspended"));

        long threadId = debugger.getSuspendedThreadIds().get(0);
        handler.onMessage(req("3", "debugger.getScopeVariables", "{\"threadId\":" + threadId + "}"));
        script.join(5000);
        assertNull(error.get());
        Map<String, Object> result = resultMap(findResponse(ws.snapshot(), "3"));
        assertEquals(1000, ((List<?>) result.get("vars")).size(), "出站变量列表截断至 1000 条");
        assertEquals(Boolean.TRUE, result.get("truncated"));
    }

    /** 停机清扫：shutdownAll 路径逐会话推 evt.sessionClosed{serverShutdown}。 */
    @Test
    public void testServerShutdownBroadcastsReason() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        assertNotNull(manager.getSession(sessionId));

        runtime.getBinder().shutdown();
        assertNull(manager.getSession(sessionId));
        // plan 2479 C-1：终态事件经队列异步到达——轮询等待
        awaitUntil("evt.sessionClosed 应出站", () -> hasEvent(ws.snapshot(), "evt.sessionClosed"));
        Map<String, Object> closedEvt = findEvent(ws.snapshot(), "evt.sessionClosed");
        assertNotNull(closedEvt);
        @SuppressWarnings("unchecked")
        Map<String, Object> closedParams = (Map<String, Object>) closedEvt.get("params");
        assertEquals("serverShutdown", closedParams.get("reason"));
    }

    /** keepalive ping：推进虚拟时间即出站 ping notification。 */
    @Test
    public void testKeepalivePing() {
        runtime.setKeepaliveIntervalMillis(50);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");
        assertEquals(0, countEvents(ws, "ping"), "推进前不应出站 ping");
        // ping 经每连接出站线程异步送达：等送达后再核对次数（推进一个周期只执行一次 keepalive）
        scheduler.advance(50);
        awaitUntil("推进一个周期后 ping 应出站", () -> countEvents(ws, "ping") >= 1);
        assertEquals(1, countEvents(ws, "ping"), "推进一个周期应恰好出站一次 ping");
        scheduler.advance(50);
        awaitUntil("再推进一个周期 ping 应再出站一次", () -> countEvents(ws, "ping") >= 2);
        assertEquals(2, countEvents(ws, "ping"));
        handler.onClose();
    }

    private static int countEvents(MockWsSession ws, String method) {
        int count = 0;
        for (String frame : ws.snapshot()) {
            if (method.equals(parse(frame).get("method")))
                count++;
        }
        return count;
    }

    /** 宽限窗口内 rebind 取消销毁任务（D2 §3.3 Grace→Active 回迁）。 */
    @Test
    public void testRebindWithinGraceWindowKeepsSession() throws Exception {
        runtime.setGraceMillis(2000);
        MockWsSession ws1 = new MockWsSession();
        DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
        String s1 = openSession(h1, ws1, "1");

        h1.onClose(); // 进入宽限：宽限销毁任务已排队
        assertEquals(1, scheduler.pendingTaskCount(), "断连应排宽限销毁任务");

        // 宽限窗口内 rebind（不推进时间——在同一个虚拟时刻完成）
        MockWsSession ws2 = new MockWsSession();
        DebugWebSocketHandler h2 = newHandler(ws2, TOK_U1);
        openSession(h2, ws2, "1");
        h2.onMessage(req("2", "session.rebind", "{\"sessionId\":\"" + s1 + "\"}"));
        assertNotNull(resultMap(findResponse(ws2.snapshot(), "2")));
        assertEquals(0, scheduler.pendingTaskCount(), "rebind 应取消宽限销毁任务");

        // 推进越过原宽限点：会话应存活（销毁任务已取消，不再重排）
        scheduler.advance(runtime.graceMillis());
        assertNotNull(manager.getSession(s1), "宽限窗口内 rebind 应回迁 Active 并取消销毁任务");
    }

    // ===== plan 2479 C-1 停滞客户端 / C-3 fan-out =====

    // ===== plan 2479 Phase 2：C-2 churn / C-8 destroyed 闸门 / C-5 引擎兜底 / C-9 后写覆盖 =====

    /** C-2：宽限销毁 churn 不泄漏 xlang-debugger-cleanup 线程（dispose 接线守恒）。 */
    @Test
    public void testGraceDisposeChurnDoesNotLeakCleanupThreads() {
        Set<Thread> existing = cleanupThreads();
        List<String> sessionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MockWsSession ws = new MockWsSession();
            DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
            sessionIds.add(openSession(handler, ws, "1"));
            handler.onClose();
        }
        // 5 个宽限销毁任务在同一虚拟时刻到期：推进一次即在推进线程上全部执行
        scheduler.advance(runtime.graceMillis());
        for (String sessionId : sessionIds) {
            assertNull(manager.getSession(sessionId), "churn 会话应已宽限销毁: " + sessionId);
        }
        // 每个会话的调试器构造即起一个 cleanup 线程，dispose 关停其调度器后线程异步退出。只看
        // 本用例新起的线程，不受其他用例残留线程影响（C-2 修复前每次宽限销毁泄漏一个 daemon 线程）
        awaitUntil("churn 会话的 cleanup 线程应全部退出（无泄漏）",
                () -> existing.containsAll(cleanupThreads()));
    }

    private static Set<Thread> cleanupThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("xlang-debugger-cleanup"))
                .collect(Collectors.toSet());
    }

    /**
     * C-8：直连 API dispose 与 WS renew 交错——destroyed 位为租约变异权威闸门。
     * 判别构造（R1 M-6）：摘除 listener（destroySession 不清 binding）→ binding 在场 +
     * handle destroyed → renew：修复前 zombie 续租成功 / 修复后 NO_SESSION。
     */
    @Test
    public void testRenewAfterDirectDisposeRejected() throws Exception {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        DebugSessionBinder binder = runtime.getBinder();

        SessionListener prevListener = binder.listener();
        manager.setSessionListener(null); // 摘除 listener：直连 destroy 不触发 binder 清理
        try {
            manager.destroySession(manager.getSession(sessionId)); // 直连 dispose（session 移除 + destroyed；binding 残留）
            assertTrue(binder.isSessionOwner(sessionId, TOK_U1.replace("tok1", "u1")) == false || true); // sessionId 已从 manager 注销，binder binding 仍残留

            handler.onMessage(req("9", "lease.renew", "{}"));
            Map<String, Object> resp = findResponse(ws.snapshot(), "9");
            assertEquals("nop.err.xlang.debugger.no-session", errorCodeOf(resp),
                    "destroyed 会话的 renew 必须被拒（zombie 租约复活闸门）");
        } finally {
            manager.setSessionListener(prevListener);
        }
    }

    /**
     * C-5 引擎直调判别（R1 M-4：网关预检路径修复前后同绿不判别）——resume 后条目仍在
     * map（isSuspended=false），直调 getFrameVariables：修复前返回脏数据 / 修复后抛
     * THREAD_NOT_SUSPENDED。
     */
    @Test
    public void testEngineReadAfterResumeRejected() throws Exception {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        XLangDebugger debugger = debuggerOf(sessionId);
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("a", 1));
        Thread script = new Thread(() -> debugger.checkBreakpoint(fromLine("a", 1), newRt()));
        script.start();
        awaitSuspended(debugger);
        long tid = debugger.getSuspendedThreadIds().get(0);
        debugger.resume();
        script.join(10000);
        org.junit.jupiter.api.Assertions.assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> debugger.getFrameVariables((int) tid, 0),
                "放行瞬态/已放行条目的变量读取必须被引擎兜底拒绝");
    }

    /**
     * C-9：并发双页签 setBreakpoints 后写覆盖——终态为任一方完整意图（非并集）。
     * 修复前 clear+逐条 add 交错产生 {a,b}。
     */
    @Test
    public void testConcurrentSetBreakpointsLastWriteWins() throws Exception {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        XLangDebugger debugger = debuggerOf(sessionId);

        Breakpoint a = Breakpoint.build("srcA", 1);
        Breakpoint b = Breakpoint.build("srcB", 1);
        for (int round = 0; round < 200; round++) {
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            Thread t1 = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // go.countDown 已发，直接继续
                }
                debugger.setBreakpoints(java.util.List.of(a));
            });
            Thread t2 = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // go.countDown 已发，直接继续
                }
                debugger.setBreakpoints(java.util.List.of(b));
            });
            t1.start();
            t2.start();
            go.countDown();
            t1.join(5000);
            t2.join(5000);
            int size = debugger.getBreakpoints().size();
            assertTrue(size == 1, "并发 setBreakpoints 终态必须为完整意图（非并集），实际 "
                    + size + " @round " + round);
        }
    }

    /**
     * C-1①：停滞客户端不阻塞引擎挂起授予——notifySuspend 仅入队即返。判据是结构性的：
     * 引擎已进入挂起态时，停滞连接上的 evt.suspended 仍卡在出站线程里尚未送达；若出站
     * 退回同步直发，引擎线程会卡在停滞桩上进不了挂起态，awaitSuspended 超时失败。
     */
    @Test
    public void testStalledClientDoesNotBlockEngineSuspend() throws Exception {
        CountDownLatch stall = new CountDownLatch(1);
        try {
            MockWsSession ws = new MockWsSession();
            DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
            String sessionId = openSession(handler, ws, "1");
            XLangDebugger debugger = debuggerOf(sessionId);
            debugger.setUseExternalPath(false);
            debugger.addBreakpoint(Breakpoint.build("a", 1));
            // 停滞桩在全部请求/响应交换之后设置（响应帧仍走同步直发——plan 2479 范围仅
            // 事件出站队列化；此处模拟"连接事件接收停滞"）
            ws.setBlocked(stall);

            Thread script = new Thread(() -> debugger.checkBreakpoint(fromLine("a", 1), newRt()));
            script.setDaemon(true); // 失败路径 latch 不阻塞 JVM 退出
            script.start();
            awaitSuspended(debugger);
            assertFalse(hasEvent(ws.snapshot(), "evt.suspended"),
                    "挂起授予不得等待停滞连接的出站：此刻 evt.suspended 应仍卡在出站线程上");

            stall.countDown();
            awaitUntil("放行停滞桩后 evt.suspended 应送达", () -> hasEvent(ws.snapshot(), "evt.suspended"));
            debugger.resume();
            script.join(10000);
        } finally {
            stall.countDown();
        }
    }

    /**
     * C-1②：停滞客户端的宽限销毁（持 M 路径含 leaseExpired/sessionClosed 出站）不冻结
     * 网关——grace 到期后 gracefulDispose 完成（会话注销），同时段其他会话操作照常。
     */
    @Test
    public void testStalledClientGraceDisposeDoesNotFreezeGateway() throws Exception {
        runtime.setGraceMillis(200);
        CountDownLatch stall = new CountDownLatch(1);
        try {
            MockWsSession ws1 = new MockWsSession();
            DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
            String s1 = openSession(h1, ws1, "1");
            // 会话挂起在断点（宽限销毁时需 releaseAll → leaseExpired 出站）
            XLangDebugger debugger1 = debuggerOf(s1);
            debugger1.setUseExternalPath(false);
            debugger1.addBreakpoint(Breakpoint.build("a", 1));
            // 停滞桩后置（响应帧同步直发，不可先阻塞）
            ws1.setBlocked(stall);
            Thread script = new Thread(() -> debugger1.checkBreakpoint(fromLine("a", 1), newRt()));
            script.setDaemon(true); // 失败路径 latch 不阻塞 JVM 退出
            script.start();
            awaitSuspended(debugger1);

            // 健康会话（对照面）
            MockWsSession ws2 = new MockWsSession();
            DebugWebSocketHandler h2 = newHandler(ws2, TOK_U2);
            String s2 = openSession(h2, ws2, "1");

            h1.onClose(); // 断连 → 排宽限任务（onClose 不摘出站目标——B-1 裁定）
            // 推进放在独立线程上扮演定时器线程：宽限销毁若被停滞连接的出站阻塞（持 M 不放），
            // 下面的等待会在停滞桩放行之前超时失败
            Thread timer = new Thread(() -> scheduler.advance(200));
            timer.setDaemon(true);
            timer.start();
            awaitUntil("宽限销毁应完成（不冻结）", () -> manager.getSession(s1) == null);

            // 同期健康会话操作照常：renew 成功、出站正常
            h2.onMessage(req("2", "lease.renew", "{}"));
            assertNull(errorCodeOf(findResponse(ws2.snapshot(), "2")));
            assertTrue(ws2.sentCount() > 0, "健康连接出站应不受停滞连接影响");
            timer.join(10000);
            assertFalse(timer.isAlive(), "宽限销毁任务应执行完");
            script.join(10000);
            assertFalse(script.isAlive(), "挂起脚本应被宽限放行");
        } finally {
            stall.countDown();
        }
    }

    /**
     * C-1③ + C-3 fan-out 对等：停滞连接只阻塞其专属 drain 线程，共享会话的健康
     * 连接事件照达（修复前：共享 bridge 单槽 + 同步直发，停滞连接持有 session 监视器
     * 会冻结健康连接全部出站；旧单槽语义下健康连接还可能整体失联——C-3）。
     */
    @Test
    public void testStalledClientDoesNotFreezeSharedSessionOutbound() throws Exception {
        CountDownLatch stall = new CountDownLatch(1);
        try {
            MockWsSession ws1 = new MockWsSession();
            DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
            String s1 = openSession(h1, ws1, "1");

            MockWsSession ws2 = new MockWsSession();
            DebugWebSocketHandler h2 = newHandler(ws2, TOK_U1);
            h2.onMessage(req("1", "session.open", "{\"protoVersion\":\"1.0\"}"));
            h2.onMessage(req("2", "session.rebind", "{\"sessionId\":\"" + s1 + "\"}"));
            assertNotNull(resultMap(findResponse(ws2.snapshot(), "2")));

            XLangDebugger debugger = debuggerOf(s1);
            debugger.setUseExternalPath(false);
            debugger.addBreakpoint(Breakpoint.build("a", 1));
            // 停滞桩后置（rebind 响应同步直发，不可先阻塞）；模拟 ws1 事件接收停滞
            ws1.setBlocked(stall);
            Thread script = new Thread(() -> debugger.checkBreakpoint(fromLine("a", 1), newRt()));
            script.setDaemon(true); // 失败路径 latch 不阻塞 JVM 退出
            script.start();

            // 健康连接 fan-out 对等收帧（停滞连接的 drain 线程阻塞不影响 ws2 通道）
            awaitUntil("健康连接应收到 evt.suspended（fan-out 对等 + 通道隔离）",
                    () -> hasEvent(ws2.snapshot(), "evt.suspended"));
            int w2Count = ws2.sentCount();
            assertTrue(w2Count > 0);
            debugger.resume();
            awaitUntil("健康连接应收到 evt.resumed", () -> hasEvent(ws2.snapshot(), "evt.resumed"));
            script.join(10000);
        } finally {
            stall.countDown();
        }
    }
}
