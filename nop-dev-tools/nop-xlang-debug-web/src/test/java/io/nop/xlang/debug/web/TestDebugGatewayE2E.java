/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.core.lang.eval.EvalRuntime;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

        handler.onMessage(req("4", "lease.renew", "{}"));
        Map<String, Object> renewResp = findResponse(ws.snapshot(), "4");
        @SuppressWarnings("unchecked")
        Map<String, Object> leaseState = (Map<String, Object>) resultMap(renewResp).get("lease");
        assertTrue(((Number) leaseState.get("expiresAtMs")).longValue() > System.currentTimeMillis());
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

        // 模拟断连（非优雅关闭）→ 宽限 200ms
        handler.onClose();

        // 宽限期满：放行（脚本第二个断点因 Destroying 授予判定①不再挂起，直接执行完）
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

    /** 多标签页：部分断连不进宽限（剩余连接续期照常）；全部断连才销毁。 */
    @Test
    public void testMultiTabLastConnectionTriggersGrace() throws Exception {
        runtime.setGraceMillis(300);
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

        // 第一连接断开：仍有绑定连接 → 不销毁
        h1.onClose();
        Thread.sleep(runtime.graceMillis() + 150);
        assertNotNull(manager.getSession(s1), "部分断连应保持会话 Active（D2 §3.3）");

        // 第二连接断开：最后连接 → 宽限 → 销毁
        h2.onClose();
        awaitUntil("宽限期满后应注销", () -> manager.getSession(s1) == null);
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
        Map<String, Object> closedEvt = findEvent(ws.snapshot(), "evt.sessionClosed");
        assertNotNull(closedEvt);
        @SuppressWarnings("unchecked")
        Map<String, Object> closedParams = (Map<String, Object>) closedEvt.get("params");
        assertEquals("serverShutdown", closedParams.get("reason"));
    }

    /** keepalive ping：短周期出站 ping notification。 */
    @Test
    public void testKeepalivePing() {
        runtime.setKeepaliveIntervalMillis(50);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");
        awaitUntil("keepalive ping 应周期出站", () -> hasEvent(ws.snapshot(), "ping"));
        handler.onClose();
    }

    /** 宽限窗口内 rebind 取消销毁任务（D2 §3.3 Grace→Active 回迁）。 */
    @Test
    public void testRebindWithinGraceWindowKeepsSession() throws Exception {
        runtime.setGraceMillis(300);
        MockWsSession ws1 = new MockWsSession();
        DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
        String s1 = openSession(h1, ws1, "1");

        h1.onClose(); // 进入宽限
        Thread.sleep(100);
        MockWsSession ws2 = new MockWsSession();
        DebugWebSocketHandler h2 = newHandler(ws2, TOK_U1);
        openSession(h2, ws2, "1");
        h2.onMessage(req("2", "session.rebind", "{\"sessionId\":\"" + s1 + "\"}"));
        assertNotNull(resultMap(findResponse(ws2.snapshot(), "2")));

        // 越过原宽限点：会话应因 rebind 取消销毁任务而存活
        Thread.sleep(runtime.graceMillis());
        assertNotNull(manager.getSession(s1), "宽限窗口内 rebind 应回迁 Active 并取消销毁任务");
    }
}
