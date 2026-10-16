/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关协议面测试（D1 §3.1/§3.2/§3.3/§3.6/§3.7/§3.8）：鉴权 4401、重复 open 4409、
 * 入站无 id -32600 计数与 4400、pong 豁免、解析失败 4400、超大帧、protoVersion、
 * tokenRefresh 三态、NO_SESSION、未知方法 -32601、debugger 方法映射抽样、
 * TOO_MANY_BPS、RESULT_TOO_LARGE、expands keys 上限。
 */
public class TestDebugGatewayProtocol extends AbstractDebugGatewayTest {

    /** D1 §3.2：无凭证 / 无效凭证 → 4401 关闭，且消息不被处理。 */
    @Test
    public void testAuthFailures4401() {
        MockWsSession wsNoToken = new MockWsSession();
        newHandler(wsNoToken, null);
        assertTrue(wsNoToken.isClosed());
        assertEquals(4401, wsNoToken.getCloseCode());

        MockWsSession wsBadToken = new MockWsSession();
        newHandler(wsBadToken, TOK_BAD);
        assertTrue(wsBadToken.isClosed());
        assertEquals(4401, wsBadToken.getCloseCode());

        MockWsSession wsUnknown = new MockWsSession();
        newHandler(wsUnknown, "unknown-token");
        assertTrue(wsUnknown.isClosed());
        assertEquals(4401, wsUnknown.getCloseCode());
    }

    /** D1 §3.4/§3.8：同连接重复 open → SESSION_ALREADY_OPEN 错误响应 + 4409 关闭（先响应后关闭）。 */
    @Test
    public void testDuplicateOpen4409() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");

        handler.onMessage(req("2", "session.open", "{\"protoVersion\":\"1.0\"}"));
        Map<String, Object> resp = findResponse(ws.snapshot(), "2");
        assertNotNull(resp);
        assertEquals("nop.err.xlang.debugger.session-already-open", errorCodeOf(resp));
        assertTrue(ws.isClosed(), "协议级会话冲突 → 4409 终态");
        assertEquals(4409, ws.getCloseCode());
    }

    /** D1 §3.7：入站无 id → id:null -32600 + 违规计数超阈值 4400；pong 豁免不计违规。 */
    @Test
    public void testNoIdViolationCountingAndPongExempt() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");
        int baseline = ws.snapshot().size();

        // pong notification：豁免（无响应、无违规）
        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"pong\"}");
        assertEquals(baseline, ws.snapshot().size(), "pong 不产生任何出站");

        // 无 id 请求 → id:null -32600，不关闭
        handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}");
        List<String> frames = ws.snapshot();
        assertEquals(baseline + 1, frames.size());
        Map<String, Object> err = parse(frames.get(frames.size() - 1));
        assertTrue(err.containsKey("error"));
        assertTrue(err.containsKey("id") && err.get("id") == null, "无 id 入站以 id:null 应答");
        assertEquals(-32600, ((Number) ((Map<?, ?>) err.get("error")).get("code")).intValue());
        assertFalse(ws.isClosed());

        // 超阈值（MAX_PROTOCOL_VIOLATIONS=10）→ 4400
        for (int i = 0; i < 20 && !ws.isClosed(); i++)
            handler.onMessage("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}");
        assertTrue(ws.isClosed(), "违规超阈值应关闭连接");
        assertEquals(4400, ws.getCloseCode());
    }

    /** D1 §3.8：解析失败 → 4400（-32700 不发射——id 未知）。 */
    @Test
    public void testParseFail4400() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        handler.onMessage("this is not json");
        assertTrue(ws.isClosed());
        assertEquals(4400, ws.getCloseCode());
        assertTrue(ws.snapshot().isEmpty(), "-32700 不发射");
    }

    /** D1 §3.1：入站单帧 256KB 上限 → -32600 + 计数。 */
    @Test
    public void testOversizeInboundFrame() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        StringBuilder sb = new StringBuilder("{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"method\":\"ping\",\"params\":{\"pad\":\"");
        sb.append("a".repeat(300 * 1024));
        sb.append("\"}}");
        handler.onMessage(sb.toString());
        List<String> frames = ws.snapshot();
        assertEquals(1, frames.size());
        assertEquals(-32600, ((Number) ((Map<?, ?>) parse(frames.get(0)).get("error")).get("code")).intValue());
        assertFalse(ws.isClosed(), "单次超限仅计数，不立即关闭");
    }

    /** D1 §3.6：protoVersion 主版本不支持 → 错误响应 + 4400；缺 protoVersion → -32602。 */
    @Test
    public void testProtoVersionNegotiation() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);

        handler.onMessage(req("1", "session.open", "{\"protoVersion\":\"2.0\"}"));
        Map<String, Object> resp = findResponse(ws.snapshot(), "1");
        assertEquals("nop.err.xlang.debugger.protocol-version-unsupported", errorCodeOf(resp));
        assertTrue(ws.isClosed());
        assertEquals(4400, ws.getCloseCode());

        MockWsSession ws2 = new MockWsSession();
        DebugWebSocketHandler handler2 = newHandler(ws2, TOK_U1);
        handler2.onMessage(req("1", "session.open", "{}"));
        assertEquals(-32602, ((Number) ((Map<?, ?>) errorOf(findResponse(ws2.snapshot(), "1"))).get("code"))
                .intValue());

        // 同主版本更高 minor：additive 演进接受并回显
        MockWsSession ws3 = new MockWsSession();
        DebugWebSocketHandler handler3 = newHandler(ws3, TOK_U1);
        handler3.onMessage(req("1", "session.open", "{\"protoVersion\":\"1.9\"}"));
        assertEquals("1.9", resultMap(findResponse(ws3.snapshot(), "1")).get("protoVersion"));
    }

    /** D1 §3.3：tokenRefresh 三态——成功 / 失败 4401 / 用户不一致 4403。 */
    @Test
    public void testTokenRefresh() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");

        // 成功：同用户 token 轮换
        handler.onMessage(req("2", "tokenRefresh", "{\"accessToken\":\"tok1\"}"));
        Map<String, Object> resp = findResponse(ws.snapshot(), "2");
        assertEquals(Boolean.TRUE, resultMap(resp).get("refreshed"));

        // 无效 token → 4401
        handler.onMessage(req("3", "tokenRefresh", "{\"accessToken\":\"tokBad\"}"));
        assertTrue(ws.isClosed());
        assertEquals(4401, ws.getCloseCode());
    }

    @Test
    public void testTokenRefreshUserMismatch4403() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");

        handler.onMessage(req("2", "tokenRefresh", "{\"accessToken\":\"tok2\"}"));
        assertTrue(ws.isClosed());
        assertEquals(4403, ws.getCloseCode(), "用户不一致 → 4403（沿用基座语义）");
    }

    /** D1 §3.4：未 open 调用 debugger.* / lease.* → NO_SESSION；session.close 无绑定幂等。 */
    @Test
    public void testNoSessionError() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);

        handler.onMessage(req("1", "debugger.isSuspended", "{}"));
        assertEquals("nop.err.xlang.debugger.no-session", errorCodeOf(findResponse(ws.snapshot(), "1")));

        handler.onMessage(req("2", "lease.renew", "{}"));
        assertEquals("nop.err.xlang.debugger.no-session", errorCodeOf(findResponse(ws.snapshot(), "2")));

        handler.onMessage(req("3", "debugger.stepInto", "{}"));
        assertEquals("nop.err.xlang.debugger.no-session", errorCodeOf(findResponse(ws.snapshot(), "3")));

        // session.close 无绑定：幂等空对象响应
        handler.onMessage(req("4", "session.close", "{}"));
        assertNotNull(resultMap(findResponse(ws.snapshot(), "4")));
    }

    /** D1 §3.6：未知方法 -32601；unsubscribe 不采纳 → -32601。 */
    @Test
    public void testUnknownMethods() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");

        handler.onMessage(req("2", "debugger.notAMethod", "{}"));
        assertEquals(-32601, ((Number) ((Map<?, ?>) errorOf(findResponse(ws.snapshot(), "2"))).get("code"))
                .intValue());

        handler.onMessage(req("3", "unsubscribe", "{}"));
        assertEquals(-32601, ((Number) ((Map<?, ?>) errorOf(findResponse(ws.snapshot(), "3"))).get("code"))
                .intValue());

        handler.onMessage(req("4", "session.unknown", "{}"));
        assertEquals(-32601, ((Number) ((Map<?, ?>) errorOf(findResponse(ws.snapshot(), "4"))).get("code"))
                .intValue());
    }

    /** 方法映射抽样（D1 §3.3）：断点 CRUD + getBreakpointAt + mute/isSuspended。 */
    @Test
    public void testDebuggerMethodMappingSamples() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        handler.onMessage(req("2", "debugger.setBreakpoints",
                "{\"bps\":[{\"sourcePath\":\"a\",\"line\":1,\"condition\":null,\"logExpr\":null}]}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "2")));

        handler.onMessage(req("3", "debugger.getBreakpoints", "{}"));
        List<?> bps = (List<?>) resultMap(findResponse(ws.snapshot(), "3")).get("bps");
        assertEquals(1, bps.size());

        handler.onMessage(req("4", "debugger.getBreakpointAt", "{\"loc\":{\"path\":\"a\",\"line\":1}}"));
        Map<String, Object> bpResult = resultMap(findResponse(ws.snapshot(), "4"));
        assertNotNull(bpResult.get("bp"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bp = (Map<String, Object>) bpResult.get("bp");
        assertEquals("a", bp.get("sourcePath"));
        assertEquals(1, ((Number) bp.get("line")).intValue());

        handler.onMessage(req("5", "debugger.addBreakpoint",
                "{\"bp\":{\"sourcePath\":\"a\",\"line\":5}}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "5")));
        handler.onMessage(req("6", "debugger.getBreakpoints", "{}"));
        assertEquals(2, ((List<?>) resultMap(findResponse(ws.snapshot(), "6")).get("bps")).size());

        handler.onMessage(req("7", "debugger.removeBreakpoint",
                "{\"bp\":{\"sourcePath\":\"a\",\"line\":5}}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "7")));

        handler.onMessage(req("8", "debugger.muteBreakpoints", "{\"muted\":true}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "8")));
        handler.onMessage(req("9", "debugger.isBreakpointsMuted", "{}"));
        assertEquals(Boolean.TRUE, resultMap(findResponse(ws.snapshot(), "9")).get("muted"));

        handler.onMessage(req("10", "debugger.isSuspended", "{}"));
        assertEquals(Boolean.FALSE, resultMap(findResponse(ws.snapshot(), "10")).get("suspended"));

        handler.onMessage(req("11", "debugger.updateBreakpoints",
                "{\"bps\":[{\"sourcePath\":\"b\",\"line\":2}],\"muted\":false}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "11")));
        handler.onMessage(req("12", "debugger.getBreakpoints", "{}"));
        assertEquals(1, ((List<?>) resultMap(findResponse(ws.snapshot(), "12")).get("bps")).size());

        // THREAD_NOT_SUSPENDED：对未挂起线程取栈
        handler.onMessage(req("13", "debugger.getStackInfo", "{\"threadId\":99}"));
        assertEquals("nop.err.xlang.debugger.thread-not-suspended",
                errorCodeOf(findResponse(ws.snapshot(), "13")));

        // suspendOnce 后未挂起线程的 stepX：THREAD_NOT_SUSPENDED（D1 §3.7 触发面）
        handler.onMessage(req("14", "debugger.stepOver", "{}"));
        assertEquals("nop.err.xlang.debugger.thread-not-suspended",
                errorCodeOf(findResponse(ws.snapshot(), "14")));

        // clearBreakpoints + getBreakpointAt 未命中 → bp:null
        handler.onMessage(req("15", "debugger.clearBreakpoints", "{}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "15")));
        handler.onMessage(req("16", "debugger.getBreakpointAt", "{\"loc\":{\"path\":\"b\",\"line\":2}}"));
        assertNull(resultMap(findResponse(ws.snapshot(), "16")).get("bp"));
    }

    /** D1 §3.1：断点 500 上限 → TOO_MANY_BPS（add/set/update 三入口）。 */
    @Test
    public void testTooManyBreakpoints() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");

        StringBuilder bpsJson = new StringBuilder("[");
        for (int i = 0; i < 501; i++) {
            if (i > 0)
                bpsJson.append(',');
            bpsJson.append("{\"sourcePath\":\"a\",\"line\":").append(i + 1).append("}");
        }
        bpsJson.append(']');

        handler.onMessage(req("2", "debugger.setBreakpoints", "{\"bps\":" + bpsJson + "}"));
        assertEquals("nop.err.xlang.debugger.too-many-bps", errorCodeOf(findResponse(ws.snapshot(), "2")));
        assertFalse(ws.isClosed(), "业务错误不关闭连接");

        // 500 恰好可接受
        StringBuilder okJson = new StringBuilder("[");
        for (int i = 0; i < 500; i++) {
            if (i > 0)
                okJson.append(',');
            okJson.append("{\"sourcePath\":\"a\",\"line\":").append(i + 1).append("}");
        }
        okJson.append(']');
        handler.onMessage(req("3", "debugger.setBreakpoints", "{\"bps\":" + okJson + "}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "3")));

        // addBreakpoint 第 501 个 → TOO_MANY_BPS
        handler.onMessage(req("4", "debugger.addBreakpoint", "{\"bp\":{\"sourcePath\":\"a\",\"line\":9999}}"));
        assertEquals("nop.err.xlang.debugger.too-many-bps", errorCodeOf(findResponse(ws.snapshot(), "4")));
    }

    /** D1 §3.1：出站单响应帧 1MB 上限 → RESULT_TOO_LARGE（降低阈值注入触发）。 */
    @Test
    public void testResultTooLarge() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = new DebugWebSocketHandler(runtime, ws, TOK_U1) {
            @Override
            protected int maxOutboundFrameBytes() {
                return 256;
            }
        };
        openSession(handler, ws, "1");

        StringBuilder bps = new StringBuilder("[");
        for (int i = 0; i < 8; i++) {
            if (i > 0)
                bps.append(',');
            bps.append("{\"sourcePath\":\"very/long/source/path/component-").append(i)
                    .append("/file.xpl\",\"line\":").append(i + 1)
                    .append(",\"condition\":\"a + b + c > 100\",\"logExpr\":null}");
        }
        bps.append(']');
        handler.onMessage(req("2", "debugger.setBreakpoints", "{\"bps\":" + bps + "}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "2")));

        // getBreakpoints 响应超 256B → RESULT_TOO_LARGE 错误响应（不发射超限帧）
        handler.onMessage(req("3", "debugger.getBreakpoints", "{}"));
        assertEquals("nop.err.xlang.debugger.result-too-large", errorCodeOf(findResponse(ws.snapshot(), "3")));
    }

    /** D1 §3.1：expandExprValue.keys ≤ 50 → -32602。 */
    @Test
    public void testExpandKeysLimit() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");

        StringBuilder keys = new StringBuilder("[");
        for (int i = 0; i < 51; i++) {
            if (i > 0)
                keys.append(',');
            keys.append("{\"name\":\"k").append(i).append("\"}");
        }
        keys.append(']');
        handler.onMessage(req("2", "debugger.expandExprValue",
                "{\"threadId\":1,\"frameIndex\":0,\"expr\":\"obj\",\"keys\":" + keys + "}"));
        assertEquals(-32602, ((Number) ((Map<?, ?>) errorOf(findResponse(ws.snapshot(), "2"))).get("code"))
                .intValue());

        // expr 长度 8KB 上限 → -32602
        String bigExpr = "x".repeat(8 * 1024 + 1);
        handler.onMessage(req("3", "debugger.getExprValue",
                "{\"threadId\":1,\"frameIndex\":0,\"expr\":\"" + bigExpr + "\"}"));
        assertEquals(-32602, ((Number) ((Map<?, ?>) errorOf(findResponse(ws.snapshot(), "3"))).get("code"))
                .intValue());
    }

    /** D1 §3.4：open 结果 schema（sessionId/protoVersion/LeasePolicy）与 evicted 会话隔离。 */
    @Test
    public void testSessionIsolationAcrossConnections() {
        MockWsSession ws1 = new MockWsSession();
        DebugWebSocketHandler h1 = newHandler(ws1, TOK_U1);
        String s1 = openSession(h1, ws1, "1");

        MockWsSession ws2 = new MockWsSession();
        DebugWebSocketHandler h2 = newHandler(ws2, TOK_U2);
        String s2 = openSession(h2, ws2, "1");

        assertFalse(s1.equals(s2), "不同连接/用户应得到独立会话");

        // u1 会话设断点不影响 u2 会话
        h1.onMessage(req("2", "debugger.setBreakpoints", "{\"bps\":[{\"sourcePath\":\"a\",\"line\":1}]}"));
        h2.onMessage(req("2", "debugger.getBreakpoints", "{}"));
        assertEquals(0, ((List<?>) resultMap(findResponse(ws2.snapshot(), "2")).get("bps")).size(),
                "会话断点表互不可见（D3 每会话实例）");
    }

    /** Breakpoint 帧往返：字符串参数到引擎 Breakpoint bean。 */
    @Test
    public void testBreakpointFrameRoundTrip() {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        openSession(handler, ws, "1");

        handler.onMessage(req("2", "debugger.addBreakpoint",
                "{\"bp\":{\"sourcePath\":\"/app/demo.xpl\",\"line\":12,\"condition\":\"x > 3\",\"logExpr\":\"hi\"}}"));
        assertNull(errorCodeOf(findResponse(ws.snapshot(), "2")));

        handler.onMessage(req("3", "debugger.getBreakpoints", "{}"));
        List<?> bps = (List<?>) resultMap(findResponse(ws.snapshot(), "3")).get("bps");
        assertEquals(1, bps.size());
        // 帧内容经 JSON 序列化往返，线上形态为对象字段（D1 §3.3 Breakpoint schema）
        Map<String, Object> bp = (Map<String, Object>) bps.get(0);
        assertEquals("/app/demo.xpl", bp.get("sourcePath"));
        assertEquals(12, ((Number) bp.get("line")).intValue());
        assertEquals("x > 3", bp.get("condition"));
        assertEquals("hi", bp.get("logExpr"));
    }
}
