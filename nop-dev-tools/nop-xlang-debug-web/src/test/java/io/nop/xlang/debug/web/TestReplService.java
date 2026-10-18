/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.debugger.Breakpoint;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.debugger.XLangDebugger;
import io.nop.xlang.debugger.session.SessionRoutingExecutor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REPL 服务端测试（plan 2473 Phase 2，D6 §3.1/§3.3/§3.4/§3.5）：门控序（401/403/
 * 属主/沙箱）、execute 端到端（超时打断/挂起计入/结果截断/同会话串行）、compile
 * 诊断与超时、审计字段。
 */
public class TestReplService extends AbstractDebugGatewayTest {
    private ReplService newService(IActionAuthChecker checker) {
        ReplService service = new ReplService(runtime, new ReplAccessGuard(
                new AbstractDebugGatewayTest.StubExtractor(), checker));
        // 单测环境无 CoreInitialization：debugger.enabled 默认 false——注入恒真谓词
        // （运行期门控本身的测试见 testRuntimeEnabledGate）
        service.setEnabledPredicate(() -> true);
        return service;
    }

    private static Map<String, Object> headers(String token) {
        Map<String, Object> headers = new HashMap<>();
        if (token != null)
            headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errOf(Map<String, Object> response) {
        return (Map<String, Object>) response.get("error");
    }

    /** execute 端到端：真会话执行 + 结果字符串化。 */
    @Test
    public void testExecuteEndToEnd() {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        Map<String, Object> response = service.execute(sessionId, "function f(v) { return v * 2; } return f(21);",
                null, headers(TOK_U1));
        assertEquals(Boolean.TRUE, response.get("ok"));
        assertEquals("42", response.get("result"));
        assertTrue((Long) response.get("durationMs") >= 0);
        assertNull(response.get("cancelled"));
    }

    /** D6 核心场景：死循环在 deadline 内被打断 → {ok:false, cancelled:true, timeout}。 */
    @Test
    public void testExecuteDeadlineInterruptsInfiniteLoop() {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        long start = System.currentTimeMillis();
        Map<String, Object> response = service.execute(sessionId, "while(true){}", 300, headers(TOK_U1));
        assertTrue(System.currentTimeMillis() - start < 10000, "死循环必须在请求线程上被打断");
        assertEquals(Boolean.FALSE, response.get("ok"));
        assertEquals(Boolean.TRUE, response.get("cancelled"));
        assertEquals("nop.err.xlang.debugger.repl-timeout", errOf(response).get("errorCode"));
    }

    /** D6 §3.4 限制钉子：断点挂起时间计入绝对时钟 deadline——resume 后首入口即超时。 */
    @Test
    public void testSuspendTimeCountsTowardDeadline() throws Exception {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        XLangDebugger debugger = debuggerOf(sessionId);
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("repl.xpl", 1));

        AtomicReference<Map<String, Object>> response = new AtomicReference<>();
        Thread worker = new Thread(() -> response.set(
                service.execute(sessionId, "return 1;", 500, headers(TOK_U1))));
        worker.start();
        awaitSuspended(debugger);
        Thread.sleep(700); // 越过 500ms deadline
        debugger.resume();
        worker.join(10000);
        assertFalse(worker.isAlive());
        assertEquals(Boolean.FALSE, response.get().get("ok"));
        assertEquals(Boolean.TRUE, response.get().get("cancelled"));
        assertEquals("nop.err.xlang.debugger.repl-timeout", errOf(response.get()).get("errorCode"));
    }

    /** D6 §3.4：同会话 execute 串行（=1），并发第二请求限流。 */
    @Test
    public void testSameSessionSerialRateLimited() throws Exception {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        XLangDebugger debugger = debuggerOf(sessionId);
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("repl.xpl", 1));

        AtomicReference<Throwable> firstError = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                service.execute(sessionId, "return 1;", 30000, headers(TOK_U1));
            } catch (Throwable t) {
                firstError.set(t);
            }
        });
        first.start();
        awaitSuspended(debugger); // 第一请求挂起在断点（in-flight）

        ReplAccessException e = (ReplAccessException) org.junit.jupiter.api.Assertions
                .assertThrows(ReplAccessException.class,
                        () -> service.execute(sessionId, "return 2;", null, headers(TOK_U1)));
        assertEquals(429, e.getHttpStatus());

        debugger.resume();
        first.join(10000);
        assertNull(firstError.get());
    }

    /** D6 §3.4 权限矩阵：未登录 401 / 无权限 403 / 跨用户属主 403（不区分语义）。 */
    @Test
    public void testGateOrder() {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        // 未登录
        ReplAccessException e1 = org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> service.execute(sessionId, "return 1;", null, headers(null)));
        assertEquals(401, e1.getHttpStatus());
        // 无权限
        ReplService noPermService = newService((perm, ctx) -> false);
        ReplAccessException e2 = org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> noPermService.execute(sessionId, "return 1;", null, headers(TOK_U1)));
        assertEquals(403, e2.getHttpStatus());
        // 跨用户属主
        ReplAccessException e3 = org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> service.execute(sessionId, "return 1;", null, headers(TOK_U2)));
        assertEquals(403, e3.getHttpStatus());
        assertEquals("nop.err.xlang.debugger.session-bound-elsewhere", e3.getErrorCode());
        // 不存在的会话同码（不区分不存在/他属）
        ReplAccessException e4 = org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> service.execute("nobody:noconn", "return 1;", null, headers(TOK_U1)));
        assertEquals(403, e4.getHttpStatus());
    }

    /** D6 §3.3 沙箱编译面：import 默认拒绝 / Promise-async 禁用 / __repl* 保留；白名单放行。 */
    @Test
    public void testSandboxValidation() {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        for (String bad : new String[]{"import io.nop.core.CoreConstants; return 1;",
                "return Promise.resolve(1);", "async function f() { return 1; }",
                "var __replDeadline = 1; return 0;"}) {
            ReplAccessException e = org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                    () -> service.execute(sessionId, bad, null, headers(TOK_U1)));
            assertEquals(400, e.getHttpStatus(), "沙箱拒绝: " + bad);
        }
        // 白名单放行
        ReplService whitelisted = newService((perm, ctx) -> true);
        runtime.setImportWhitelist("java.lang.String");
        try {
            Map<String, Object> response = whitelisted.execute(sessionId,
                    "import java.lang.String; return 1;", null, headers(TOK_U1));
            assertEquals(Boolean.TRUE, response.get("ok"));
        } finally {
            runtime.setImportWhitelist("");
        }
    }

    /**
     * plan 2478 SEC-01/02/03 三探针（item 25 审计）：词法 IMPORT_PATTERN 的三形态绕过
     * 必须被 parse-only AST 白名单校验拒绝。探针为良性形态（仅 import+return 常量，
     * 不含危险调用）——修复前红 = 请求成功无 400。
     */
    @Test
    public void testAstImportBypassProbes() {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        // SEC-01：无分号 ASI import（词法 ^\s*import\s+[\w.]+\s*; 强制分号——零匹配）
        // SEC-03：字面量 xlib import（[\w.]+ 无法匹配带引号路径）
        String[] probes = {
                "import io.nop.core.CoreConstants\nreturn 1;",
                "import \"/nop/core/xlib/test.xlib\" as T;\nreturn 1;"};
        for (String bad : probes) {
            ReplAccessException e = org.junit.jupiter.api.Assertions.assertThrows(
                    ReplAccessException.class,
                    () -> service.execute(sessionId, bad, null, headers(TOK_U1)));
            assertEquals(400, e.getHttpStatus(), "AST 白名单拒绝: " + bad);
        }

        // SEC-02：同行双 import——首项在白名单、次项白名单外，次项必须被拒
        //（词法层首 match 后同行 ^ 锚定失效，对次项零校验）
        runtime.setImportWhitelist("java.lang.String");
        try {
            ReplAccessException e = org.junit.jupiter.api.Assertions.assertThrows(
                    ReplAccessException.class,
                    () -> service.execute(sessionId,
                            "import java.lang.String; import io.nop.core.CoreConstants; return 1;",
                            null, headers(TOK_U1)));
            assertEquals(400, e.getHttpStatus(), "SEC-02 同行第二 import 须被拒绝");
            Map<String, Object> ok = service.execute(sessionId,
                    "import java.lang.String; return 1;", null, headers(TOK_U1));
            assertEquals(Boolean.TRUE, ok.get("ok"));
        } finally {
            runtime.setImportWhitelist("");
        }
    }

    /** plan 2478 SEC-04/06：REPL 编译面禁用零 import 能力面函数（inject/loadDslModel/loadDeltaJson）与正则构造（RegExp/正则字面量）。 */
    @Test
    public void testBannedConstructs() {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        String[] banned = {
                "return inject('nopDao');",
                "return loadDslModel('/nop/demo/orm/app.orm.xml');",
                "return loadDeltaJson('/nop/demo/x.json');",
                "return new RegExp('(a+)+$').test('aaa');",
                "return /(a+)+$/.test('aaab');"};
        for (String bad : banned) {
            ReplAccessException e = org.junit.jupiter.api.Assertions.assertThrows(
                    ReplAccessException.class,
                    () -> service.execute(sessionId, bad, null, headers(TOK_U1)));
            assertEquals(400, e.getHttpStatus(), "禁用构造拒绝: " + bad);
        }
    }

    /**
     * plan 2478 SEC-07：watch 求值 deadline 窗口接线——超时经取消通道在求值入口打断并
     * 映射 -32000 错误响应（修复前 getExprValue 无任何时间界，本用例修复前红）。
     * 探针形态裁定：表达式文法无语句循环，SimpleExprParser 不接受块体 lambda（实测
     * missing-factor）——watch 内多入口打断依赖函数调用边界经全局执行器 per-entry
     * 检查，与 REPL execute 死循环打断同机制（testExecuteDeadlineInterruptsInfiniteLoop
     * 已钉）。本用例以 0 时长窗口使引擎 eval 入口检查即时触发，一次钉住三点接线：
     * handler 窗口 begin/end、引擎 eval 入口 checkDeadline、Error 系 ReplCancelledError
     * 显式 catch 映射。
     */
    @Test
    public void testWatchEvalDeadlineWindowInterrupts() throws Exception {
        runtime.setWatchEvalTimeoutMillis(0);
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        XLangDebugger debugger = debuggerOf(sessionId);
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("repl.xpl", 1));

        AtomicReference<Map<String, Object>> workerResp = new AtomicReference<>();
        Thread worker = new Thread(() -> workerResp.set(
                service.execute(sessionId, "return 1;", 30000, headers(TOK_U1))));
        worker.start();
        awaitSuspended(debugger);
        long tid = debugger.getSuspendedThreadIds().get(0);
        try {
            handler.onMessage(req("10", "debugger.getExprValue",
                    "{\"threadId\":" + tid + ",\"frameIndex\":0,\"expr\":\"1\"}"));
            awaitUntil("watch 求值须在窗口到期时返回错误响应",
                    () -> findResponse(ws.snapshot(), "10") != null);
            Map<String, Object> resp = findResponse(ws.snapshot(), "10");
            assertEquals("nop.err.xlang.debugger.repl-timeout", errorCodeOf(resp));

            // 正常求值不受影响（窗口按请求开关）：worker 放行后再次挂起求值返回正常值——
            // 由 testGetExprValue 既有流程覆盖，这里仅确认 worker 可正常完成
            debugger.resume();
            worker.join(10000);
            assertFalse(worker.isAlive());
            assertEquals(Boolean.TRUE, workerResp.get().get("ok"));
        } finally {
            runtime.setWatchEvalTimeoutMillis(-1);
        }
    }

    /** plan 2478 SEC-07：watch 表达式经完整沙箱校验（与 REPL 同一禁用清单）——import/正则字面量/inject 按 -32602 拒绝。 */
    @Test
    public void testWatchExprValidation() throws Exception {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        XLangDebugger debugger = debuggerOf(sessionId);
        debugger.setUseExternalPath(false);
        debugger.addBreakpoint(Breakpoint.build("repl.xpl", 1));

        AtomicReference<Map<String, Object>> workerResp = new AtomicReference<>();
        Thread worker = new Thread(() -> workerResp.set(
                service.execute(sessionId, "return 1;", 30000, headers(TOK_U1))));
        worker.start();
        awaitSuspended(debugger);
        long tid = debugger.getSuspendedThreadIds().get(0);
        try {
            String[] banned = {
                    "import java.lang.Runtime; 1",
                    "return /(a+)+$/.test('aaab');",
                    "inject('nopDao')"};
            for (int i = 0; i < banned.length; i++) {
                String expr = banned[i].replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n");
                handler.onMessage(req("w" + i, "debugger.getExprValue",
                        "{\"threadId\":" + tid + ",\"frameIndex\":0,\"expr\":\"" + expr + "\"}"));
                Map<String, Object> resp = findResponse(ws.snapshot(), "w" + i);
                assertNotNull(resp, "watch 违规表达式应有错误响应: " + banned[i]);
                Map<?, ?> err = (Map<?, ?>) resp.get("error");
                assertNotNull(err);
                assertEquals(-32602, ((Number) err.get("code")).intValue(),
                        "watch 违规表达式须按 invalidParams 拒绝: " + banned[i]);
            }
        } finally {
            debugger.resume();
            worker.join(10000);
            assertFalse(worker.isAlive());
        }
    }

    /** plan 2478 SEC-08：compile 门控与 execute 对齐——enabled=false 403；全局并发耗尽 429。 */
    @Test
    public void testCompileEnabledGateAndConcurrency() throws Exception {
        ReplService service = newService((perm, ctx) -> true);
        assertTrue(((List<?>) service.compile("repl.xpl", "return 1;", headers(TOK_U1)).get("diagnostics")).isEmpty());

        // enabled 运行期谓词对齐（原 compile 无此复查——与 execute 门控序不对称）
        service.setEnabledPredicate(() -> false);
        try {
            ReplAccessException e403 = org.junit.jupiter.api.Assertions.assertThrows(
                    ReplAccessException.class,
                    () -> service.compile("repl.xpl", "return 1;", headers(TOK_U1)));
            assertEquals(403, e403.getHttpStatus());
        } finally {
            service.setEnabledPredicate(() -> true);
        }

        // 并发门：4 个会话全部挂起在断点（各持 1 permit）→ compile 429
        int maxConcurrent = 4;
        DebugWebSocketHandler[] handlers = new DebugWebSocketHandler[maxConcurrent];
        MockWsSession[] sessions = new MockWsSession[maxConcurrent];
        XLangDebugger[] debuggers = new XLangDebugger[maxConcurrent];
        Thread[] workers = new Thread[maxConcurrent];
        String[] sessionIds = new String[maxConcurrent];
        try {
            for (int i = 0; i < maxConcurrent; i++) {
                sessions[i] = new MockWsSession();
                handlers[i] = newHandler(sessions[i], TOK_U1);
                sessionIds[i] = openSession(handlers[i], sessions[i], String.valueOf(i + 1));
                debuggers[i] = debuggerOf(sessionIds[i]);
                debuggers[i].setUseExternalPath(false);
                debuggers[i].addBreakpoint(Breakpoint.build("repl.xpl", 1));
                final String sid = sessionIds[i];
                workers[i] = new Thread(() -> service.execute(sid,
                        "return 1;", 30000, headers(TOK_U1)));
                workers[i].start();
            }
            for (int i = 0; i < maxConcurrent; i++)
                awaitSuspended(debuggers[i]);

            ReplAccessException e429 = org.junit.jupiter.api.Assertions.assertThrows(
                    ReplAccessException.class,
                    () -> service.compile("repl.xpl", "return 1;", headers(TOK_U1)));
            assertEquals(429, e429.getHttpStatus());
        } finally {
            for (int i = 0; i < maxConcurrent; i++) {
                try {
                    if (debuggers[i] != null)
                        debuggers[i].resume();
                } catch (Exception ignore) {
                    // 会话可能已被清理
                }
            }
            for (int i = 0; i < maxConcurrent; i++)
                if (workers[i] != null)
                    workers[i].join(10000);
        }
    }

    /** plan 2478 SEC-09：拒绝路径 denied 审计（401 actor 未知照写 / 403 actor 已知 / 沙箱 400）+ Error 终止路径 error 审计。 */
    @Test
    public void testDeniedAndErrorAuditEvents() {
        ReplService service = newService((perm, ctx) -> true);
        List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
        service.setAuditConsumer(events::add);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        // 401：actor 未知照写 denied
        org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> service.compile("repl.xpl", "return 1;", headers(null)));
        assertEquals("denied", events.get(0).get("result"));
        assertNull(events.get(0).get("actor"));

        // 403 无权限：actor 已知
        ReplService noPerm = newService((perm, ctx) -> false);
        List<Map<String, Object>> noPermEvents = new CopyOnWriteArrayList<>();
        noPerm.setAuditConsumer(noPermEvents::add);
        org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> noPerm.execute("nobody:noconn", "return 1;", null, headers(TOK_U1)));
        assertEquals("denied", noPermEvents.get(0).get("result"));
        assertEquals("u1", noPermEvents.get(0).get("actor"));

        // 沙箱违规 400：denied
        org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> service.execute(sessionId, "import io.nop.core.CoreConstants; return 1;", null, headers(TOK_U1)));
        assertEquals("denied", events.get(1).get("result"));

        // Error 终止路径：SOE 深递归（无论裸 Error 重抛还是包装为 error 响应）必留 result=error 审计
        int before = events.size();
        boolean threw;
        try {
            service.execute(sessionId, "function f(n){ return f(n + 1); } return f(0);", 30000, headers(TOK_U1));
            threw = false;
        } catch (Throwable t) {
            threw = true;
        }
        assertTrue(threw || events.size() == before + 1, "SOE 必须重抛或以错误响应呈现");
        assertEquals("error", events.get(events.size() - 1).get("result"), "Error 终止路径必须写审计");
    }

    /** plan 2478 SEC-10：setImportWhitelist(null) 清除 override——回落 CFG（默认空白名单），import 被拒。 */
    @Test
    public void testWhitelistOverrideClear() {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        runtime.setImportWhitelist("java.lang.String");
        assertEquals(Boolean.TRUE, service.execute(sessionId,
                "import java.lang.String; return 1;", null, headers(TOK_U1)).get("ok"));
        // 清除 override → 回落 CFG（默认 ""）→ 默认拒绝恢复
        runtime.setImportWhitelist(null);
        assertEquals("", runtime.importWhitelist());
        ReplAccessException e = org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> service.execute(sessionId, "import java.lang.String; return 1;", null, headers(TOK_U1)));
        assertEquals(400, e.getHttpStatus());
    }

    /** D6 §3.4：结果 1MB 截断 + truncated 标记。 */
    @Test
    public void testResultTruncation() {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        Map<String, Object> response = service.execute(sessionId,
                "return '0123456789'.repeat(200000);",
                null, headers(TOK_U1));
        assertEquals(Boolean.TRUE, response.get("ok"));
        String result = (String) response.get("result");
        assertTrue(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= ReplService.MAX_RESULT_BYTES + 4);
        assertEquals(Boolean.TRUE, response.get("truncated"));
    }

    /** compile 端点：诊断映射（编译错误 → error 诊断）+ 成功空诊断。 */
    @Test
    public void testCompileDiagnostics() {
        ReplService service = newService((perm, ctx) -> true);

        Map<String, Object> ok = service.compile("repl.xpl", "return 1 + 1;", headers(TOK_U1));
        assertTrue(((List<?>) ok.get("diagnostics")).isEmpty());

        Map<String, Object> bad = service.compile("repl.xpl", "return ???;", headers(TOK_U1));
        List<?> diagnostics = (List<?>) bad.get("diagnostics");
        assertFalse(diagnostics.isEmpty(), "编译错误应产生诊断");
    }

    /** compile 门控：未登录 401 / 无权限 403。 */
    @Test
    public void testCompileGate() {
        ReplService service = newService((perm, ctx) -> true);
        ReplAccessException e1 = org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> service.compile(null, "return 1;", headers(null)));
        assertEquals(401, e1.getHttpStatus());
        ReplService noPerm = newService((perm2, ctx) -> false);
        ReplAccessException e2 = org.junit.jupiter.api.Assertions.assertThrows(ReplAccessException.class,
                () -> noPerm.compile(null, "return 1;", headers(TOK_U1)));
        assertEquals(403, e2.getHttpStatus());
    }

    /** D6 §3.5 审计：八字段齐备、compile/execute 双 action、digest/preview。 */
    @Test
    public void testAuditEvents() {
        ReplService service = newService((perm, ctx) -> true);
        List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
        service.setAuditConsumer(events::add);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        service.compile("repl.xpl", "return 1;", headers(TOK_U1));
        service.execute(sessionId, "return 2;", null, headers(TOK_U1));

        assertTrue(events.size() >= 2);
        Map<String, Object> compileEvent = events.get(0);
        assertEquals("compile", compileEvent.get("action"));
        assertEquals("u1", compileEvent.get("actor"));
        assertNull(compileEvent.get("sessionId"));
        assertNotNull(compileEvent.get("sourceDigest"));
        assertNotNull(compileEvent.get("sourcePreview"));
        assertEquals("ok", compileEvent.get("result"));
        assertNotNull(compileEvent.get("timestamp"));
        assertNotNull(compileEvent.get("durationMs"));
        Map<String, Object> executeEvent = events.get(1);
        assertEquals("execute", executeEvent.get("action"));
        assertEquals(sessionId, executeEvent.get("sessionId"));
    }

    /** debugger.enabled 运行期复查（D6 §一.5 运行层门控）——配置翻 false 即拒绝。 */
    @Test
    public void testRuntimeEnabledGate() {
        ReplService service = newService((perm, ctx) -> true);
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");

        service.setEnabledPredicate(() -> false);
        try {
            ReplAccessException e = org.junit.jupiter.api.Assertions.assertThrows(
                    ReplAccessException.class,
                    () -> service.execute(sessionId, "return 1;", null, headers(TOK_U1)));
            assertEquals(403, e.getHttpStatus());
        } finally {
            service.setEnabledPredicate(io.nop.xlang.XLangConfigs.CFG_XLANG_DEBUGGER_ENABLED::get);
        }
    }
}
