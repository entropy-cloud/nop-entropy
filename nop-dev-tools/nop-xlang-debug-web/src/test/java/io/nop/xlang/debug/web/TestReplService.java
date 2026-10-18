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
import io.nop.xlang.debugger.XLangDebugger;
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
