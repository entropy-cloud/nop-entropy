/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.commons.test.time.ManualClock;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.nop.api.core.util.SourceLocation.fromLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志攒批管道与异常事件出站测试（plan 2472 Phase 2，M3 验收"日志/事件经网关按会话
 * 定向出站且顺序正确（攒批不乱序）"）：阈值/定时/销毁 flush、攒批保序、会话隔离、
 * rebind 重接、evt.exception 出站、logpoint 端到端。
 */
public class TestDebugLogPipe extends AbstractDebugGatewayTest {

    private void setupShortFlush(int batchSize, long flushIntervalMillis) {
        runtime.setLogBatchSize(batchSize);
        runtime.setLogFlushIntervalMillis(flushIntervalMillis);
    }

    /** 阈值 flush：达 batch-size 立即出站，entries 保序且带 timestamp。 */
    @Test
    public void testLogBatchingByThresholdOrdered() {
        useManualClock();
        setupShortFlush(3, 60_000);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        GatewayNotifierBridge bridge = runtime.getBinder().getBridge(sessionId);
        assertNotNull(bridge);
        bridge.notifyLog(null, "a.xpl", 1, "m1");
        bridge.notifyLog(null, "a.xpl", 2, "m2");
        assertTrue(ws.snapshot().stream().noneMatch(f -> f.contains("evt.logs")),
                "未达阈值不应提前 flush");
        bridge.notifyLog(null, "a.xpl", 3, "m3");

        awaitUntil("evt.logs 应出站", () -> hasEvent(ws.snapshot(), "evt.logs"));
        Map<String, Object> evt = findEvent(ws.snapshot(), "evt.logs");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) ((Map<String, Object>) evt.get("params"))
                .get("entries");
        assertEquals(3, entries.size(), "阈值 flush 应一次携带 3 条");
        assertEquals("m1", entries.get(0).get("message"));
        assertEquals("m2", entries.get(1).get("message"));
        assertEquals("m3", entries.get(2).get("message"));
        assertEquals(1, ((Number) entries.get(0).get("line")).intValue());
        assertNotNull(entries.get(0).get("timestamp"), "D1 §3.5 evt.log schema 应带 timestamp");
        assertEquals(clock.peekMillis(), ((Number) entries.get(2).get("timestamp")).longValue(),
                "日志时间戳取自 CoreMetrics 时钟");
    }

    /** 定时 flush：未达阈值按 flush-interval 出站。 */
    @Test
    public void testLogTimedFlush() {
        setupShortFlush(100, 50);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        int pendingBefore = scheduler.pendingTaskCount();
        runtime.getBinder().getBridge(sessionId).notifyLog(null, null, 0, "single");
        assertEquals(pendingBefore + 1, scheduler.pendingTaskCount(), "未达阈值应排一次定时 flush");
        assertFalse(hasEvent(ws.snapshot(), "evt.logs"), "定时 flush 到期前不应出站");

        scheduler.advance(50);
        awaitUntil("定时 flush 应出站", () -> hasEvent(ws.snapshot(), "evt.logs"));
        Map<String, Object> evt = findEvent(ws.snapshot(), "evt.logs");
        @SuppressWarnings("unchecked")
        List<?> entries = (List<?>) ((Map<String, Object>) evt.get("params")).get("entries");
        assertEquals(1, entries.size());
    }

    /** 会话隔离：A 会话日志只出站到 A 连接，B 会话不可见。 */
    @Test
    public void testLogSessionIsolation() {
        setupShortFlush(2, 60_000);
        MockWsSession ws1 = new MockWsSession();
        MockWsSession ws2 = new MockWsSession();
        DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
        DebugWebSocketHandler h2 = newHandler(ws2, TOK_U2);
        String s1 = openSession(h1, ws1, "1");
        String s2 = openSession(h2, ws2, "1");

        runtime.getBinder().getBridge(s1).notifyLog(null, null, 0, "from-A-1");
        runtime.getBinder().getBridge(s1).notifyLog(null, null, 0, "from-A-2");
        awaitUntil("A 会话 evt.logs 应出站", () -> hasEvent(ws1.snapshot(), "evt.logs"));
        assertTrue(ws1.snapshot().stream().anyMatch(f -> f.contains("from-A-1")));
        assertFalse(ws2.snapshot().stream().anyMatch(f -> f.contains("evt.logs")
                || f.contains("from-A")), "B 会话不应收到 A 的日志");
    }

    /** 销毁 flush：session.close 残余 buffer 先于 evt.sessionClosed 出站（保序）。 */
    @Test
    public void testDestroyFlushBeforeSessionClosed() {
        setupShortFlush(100, 60_000);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        runtime.getBinder().getBridge(sessionId).notifyLog(null, "a.xpl", 5, "pending-log");
        assertTrue(ws.snapshot().stream().noneMatch(f -> f.contains("evt.logs")), "残余应留 buffer");

        handler.onMessage(req("2", "session.close", "{}"));
        // plan 2479 C-1：事件经队列异步到达——轮询等待后断言次序（保序语义：同连接
        // 队列 FIFO，destroyFlush 先于 sessionClosed 入队）
        awaitUntil("销毁 flush 应送达残余日志", () -> hasEvent(ws.snapshot(), "evt.logs"));
        awaitUntil("evt.sessionClosed 应出站", () -> hasEvent(ws.snapshot(), "evt.sessionClosed"));
        List<String> frames = ws.snapshot();
        int logsIdx = -1;
        int closedIdx = -1;
        for (int i = 0; i < frames.size(); i++) {
            if ("evt.logs".equals(parse(frames.get(i)).get("method")))
                logsIdx = i;
            if ("evt.sessionClosed".equals(parse(frames.get(i)).get("method")))
                closedIdx = i;
        }
        assertTrue(logsIdx >= 0, "销毁 flush 应送达残余日志");
        assertTrue(closedIdx >= 0);
        assertTrue(logsIdx < closedIdx, "evt.logs 必须先于 evt.sessionClosed");
    }

    /** rebind 重接：目标会话 pipe 换绑新 bridge，日志出站到新连接。 */
    @Test
    public void testRebindRewiresPipeSink() {
        setupShortFlush(2, 60_000);
        MockWsSession ws1 = new MockWsSession();
        DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
        String s1 = openSession(h1, ws1, "1");

        MockWsSession ws2 = new MockWsSession();
        DebugWebSocketHandler h2 = newHandler(ws2, TOK_U1);
        openSession(h2, ws2, "1"); // 过渡会话
        h2.onMessage(req("2", "session.rebind", "{\"sessionId\":\"" + s1 + "\"}"));
        assertNotNull(resultMap(findResponse(ws2.snapshot(), "2")));

        runtime.getBinder().getBridge(s1).notifyLog(null, null, 0, "after-rebind-1");
        runtime.getBinder().getBridge(s1).notifyLog(null, null, 0, "after-rebind-2");
        awaitUntil("rebind 后 evt.logs 应出站到新连接", () -> hasEvent(ws2.snapshot(), "evt.logs"));
        assertTrue(ws2.snapshot().stream().anyMatch(f -> f.contains("after-rebind-2")));
        // plan 2479 C-3 fan-out 语义反转：rebind 复用 bridge 追加目标，旧连接保持
        // 对等接收（废除 last-rebind-wins——item 24 审计 C-3 / 前端审计 F-7 销案）
        awaitUntil("旧连接 fan-out 对等接收", () -> ws1.snapshot().stream().anyMatch(f -> f.contains("after-rebind")));
    }

    /** evt.exception 出站：四字段承载 + 无定位（line<=0）形态不发射 sourcePath/line。 */
    @Test
    public void testExceptionEventOutbound() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        GatewayNotifierBridge bridge = runtime.getBinder().getBridge(sessionId);
        bridge.notifyException("/app/demo.xpl", 12, "boom", "nop.err.x.demo");
        awaitUntil("evt.exception 应出站", () -> hasEvent(ws.snapshot(), "evt.exception"));
        Map<String, Object> params = paramsOf(findEvent(ws.snapshot(), "evt.exception"));
        assertEquals("boom", params.get("message"));
        assertEquals("nop.err.x.demo", params.get("errorCode"));
        assertEquals("/app/demo.xpl", params.get("sourcePath"));
        assertEquals(12, ((Number) params.get("line")).intValue());

        bridge.notifyException(null, -1, "no-loc", null);
        awaitUntil("第二例应出站", () -> ws.snapshot().stream().filter(f -> f.contains("evt.exception")).count() >= 2);
        List<String> frames = ws.snapshot();
        Map<String, Object> noLoc = null;
        for (String frame : frames) {
            if (frame.contains("evt.exception") && frame.contains("no-loc"))
                noLoc = parse(frame);
        }
        assertNotNull(noLoc);
        Map<String, Object> noLocParams = paramsOf(noLoc);
        assertEquals("no-loc", noLocParams.get("message"));
        assertFalse(noLocParams.containsKey("sourcePath"), "无定位不发射 sourcePath");
        assertFalse(noLocParams.containsKey("line"), "无定位不发射 line");
        assertFalse(noLocParams.containsKey("errorCode"), "null errorCode 不发射");
    }

    /** logpoint 端到端：logExpr 断点命中 → notifyLog → 攒批 → evt.logs（真实产生源）。 */
    @Test
    public void testLogpointEndToEnd() throws Exception {
        setupShortFlush(1, 60_000);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        XLangDebugger debugger = debuggerOf(sessionId);
        debugger.setUseExternalPath(false);

        // logExpr 断点：仅打日志不挂起
        handler.onMessage(req("2", "debugger.addBreakpoint",
                "{\"bp\":{\"sourcePath\":\"a\",\"line\":1,\"logExpr\":\"'logpoint-hit'\"}}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "2")));

        Thread script = startSuspendedHit(debugger, null, fromLine("a", 1));
        script.join(5000);
        assertFalse(script.isAlive(), "logpoint 不挂起，脚本直接执行完");

        awaitUntil("logpoint 日志应攒批出站", () -> hasEvent(ws.snapshot(), "evt.logs"));
        Map<String, Object> evt = findEvent(ws.snapshot(), "evt.logs");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) ((Map<String, Object>) evt.get("params"))
                .get("entries");
        assertEquals("logpoint-hit", entries.get(0).get("message"));
        assertEquals("a", entries.get(0).get("sourcePath"));
        assertTrue(ws.snapshot().stream().noneMatch(f -> f.contains("evt.suspended")),
                "logpoint 不产生挂起事件");
    }

    /** pipe 直发兜底：未接 pipe 的 bridge notifyLog 走单条 evt.log（非 no-op）。 */
    @Test
    public void testDirectSendFallbackWithoutPipe() {
        MockWsSession ws = new MockWsSession();
        GatewayNotifierBridge bridge = new GatewayNotifierBridge(ws);
        bridge.notifyLog(null, "a.xpl", 1, "direct");
        assertEquals(1, ws.snapshot().size());
        Map<String, Object> frame = parse(ws.snapshot().get(0));
        assertEquals("evt.log", frame.get("method"));
        assertEquals("direct", paramsOf(frame).get("message"));
    }

    private Map<String, Object> paramsOf(Map<String, Object> eventFrame) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) eventFrame.get("params");
        return params;
    }

    /** 审计 F3：evt.logs 与 evt.suspended 共享 session 出站锁全序（结构性保证的断言面）。 */
    @Test
    public void testLogsAndSuspendedShareOutboundOrder() {
        setupShortFlush(1, 60_000);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        GatewayNotifierBridge bridge = runtime.getBinder().getBridge(sessionId);
        assertNotNull(bridge);
        // 同线程顺序触发：log（batch=1 同步 flush）→ suspend 事件
        bridge.notifyLog(null, "a.xpl", 1, "ordered-log");
        bridge.notifySuspend(null, fromLine("a.xpl", 2), null);

        // plan 2479 C-1：suspend 事件经每连接队列异步到达——轮询等待后断言次序
        //（保序语义：pipe flush 与事件帧同一连接队列 FIFO）
        awaitUntil("evt.suspended 应出站", () -> hasEvent(ws.snapshot(), "evt.suspended"));
        List<String> frames = ws.snapshot();
        int logsIdx = -1;
        int suspendedIdx = -1;
        for (int i = 0; i < frames.size(); i++) {
            String method = (String) parse(frames.get(i)).get("method");
            if ("evt.logs".equals(method) && logsIdx < 0)
                logsIdx = i;
            if ("evt.suspended".equals(method) && suspendedIdx < 0)
                suspendedIdx = i;
        }
        assertTrue(logsIdx >= 0 && suspendedIdx >= 0);
        assertTrue(logsIdx < suspendedIdx, "同线程触发序应等于出站帧序（共享出站锁全序）");
    }
}
