/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.exceptions.NopLoginException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.Breakpoint;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.xlang.api.XLang;
import io.nop.xlang.debugger.XLangDebugger;
import io.nop.xlang.debugger.session.SessionDebugManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static io.nop.api.core.util.SourceLocation.fromLine;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 网关测试基座：manager 单例初始化 + 注入型 runtime + 桩鉴权
 * （tok1→u1、tok2→u2、tokBad→NopLoginException、未知→null=4401）。
 * <p>
 * runtime 的定时器是 {@link ManualScheduler}：宽限销毁、keepalive、日志定时 flush 只在测试
 * {@code scheduler.advance(...)} 时触发。需要让 CoreMetrics 时间（租约、REPL deadline、日志时间戳）
 * 也走同一条虚拟时间线的用例调用 {@link #useManualClock()}，基座在 tearDown 恢复默认时钟。
 */
public abstract class AbstractDebugGatewayTest {
    static final String TOK_U1 = "tok1";
    static final String TOK_U2 = "tok2";
    static final String TOK_BAD = "tokBad";

    SessionDebugManager manager;
    DebugGatewayRuntime runtime;
    ManualClock clock;
    ManualScheduler scheduler;

    static class StubExtractor implements IUserContextExtractor {
        @Override
        public IUserContext extractFromHeaders(Map<String, Object> headers) throws NopLoginException {
            Object auth = headers.get("Authorization");
            if (!(auth instanceof String) || !((String) auth).startsWith("Bearer "))
                return null;
            String token = ((String) auth).substring("Bearer ".length());
            switch (token) {
                case TOK_U1:
                    return new TestUserContext("u1");
                case TOK_U2:
                    return new TestUserContext("u2");
                case TOK_BAD:
                    throw new NopLoginException("nop.err.test.invalid-token", null, false, false);
                default:
                    return null;
            }
        }
    }

    @BeforeEach
    public void setUpGateway() {
        manager = SessionDebugManager.instance();
        manager.init(new XLangDebugger());
        clock = ManualClock.startingNow();
        scheduler = new ManualScheduler(clock);
        runtime = new DebugGatewayRuntime(manager, new StubExtractor(), scheduler);
        // plan 2478 SEC-05：基座注入 permissive checker（全部权限放行）——生产装配
        // （DebugWebServerConfig）checker 缺失时 fail-closed 拒绝；该分支由专项测试
        // （TestDebugGatewayProtocol 权限用例）以无 checker runtime 显式覆盖，
        // 禁止以放软生产 fail-closed 语义为代价保测试绿
        runtime.setAuthChecker((perm, ctx) -> true);
        // 确定性序列断言：keepalive 禁用（专项测试自行开启短周期）
        runtime.setKeepaliveIntervalMillis(0);
        runtime.setRenewIntervalMillis(10000);
        runtime.setGraceMillis(15000);
        runtime.getBinder().install();
    }

    @AfterEach
    public void tearDownGateway() {
        try {
            runtime.getBinder().shutdown();
        } finally {
            runtime.shutdownTimer();
            manager.reset();
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }

    /** 让 CoreMetrics 读本用例的虚拟时钟（与 scheduler 同一时间线）；tearDown 恢复默认时钟。 */
    void useManualClock() {
        CoreMetrics.registerClock(clock);
    }

    /** 等某线程在虚拟时钟上的读取次数达到 reads（REPL deadline 检查每个融合执行器入口读一次）。 */
    void awaitClockReads(Thread thread, long reads) {
        awaitUntil("线程未在 10s 内读满 " + reads + " 次时钟", () -> clock.readsBy(thread) >= reads);
    }

    DebugWebSocketHandler newHandler(MockWsSession ws, String token) {
        return runtime.createHandler(ws, token);
    }

    /** 构造带 id 的 JSON-RPC 请求帧。 */
    static String req(String id, String method, String paramsJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":\"").append(id).append("\",\"method\":\"").append(method)
                .append("\"");
        if (paramsJson != null)
            sb.append(",\"params\":").append(paramsJson);
        sb.append("}");
        return sb.toString();
    }

    static Map<String, Object> parse(String json) {
        return JsonTool.parseMap(json);
    }

    /** 按 id 查找响应帧（result 或 error）。 */
    static Map<String, Object> findResponse(List<String> frames, String id) {
        for (String frame : frames) {
            Map<String, Object> msg = parse(frame);
            if (id.equals(String.valueOf(msg.get("id"))) && (msg.containsKey("result") || msg.containsKey("error")))
                return msg;
        }
        return null;
    }

    /** 查找第一个服务端事件帧（无 id、带 method）。 */
    static Map<String, Object> findEvent(List<String> frames, String method) {
        for (String frame : frames) {
            Map<String, Object> msg = parse(frame);
            if (method.equals(msg.get("method")) && !msg.containsKey("id"))
                return msg;
        }
        return null;
    }

    static boolean hasEvent(List<String> frames, String method) {
        return findEvent(frames, method) != null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> resultMap(Map<String, Object> response) {
        return (Map<String, Object>) response.get("result");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> errorOf(Map<String, Object> response) {
        return (Map<String, Object>) response.get("error");
    }

    static String errorCodeOf(Map<String, Object> response) {
        Map<String, Object> error = errorOf(response);
        return error == null ? null : (String) error.get("errorCode");
    }

    static void awaitUntil(String message, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean())
                return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(message);
            }
        }
        fail(message);
    }

    static EvalRuntime newRt() {
        IEvalScope scope = XLang.newEvalScope();
        return new EvalRuntime(scope);
    }

    static void awaitSuspended(XLangDebugger debugger) {
        awaitUntil("debugger 未在 10s 内挂起", debugger::isSuspended);
    }

    /** 挂起于断点的后台线程（照 TestLeaseLifecycle 编排模式）。 */
    static Thread startSuspendedHit(XLangDebugger debugger, AtomicReference<Throwable> error,
                                    SourceLocation loc) {
        Thread thread = new Thread(() -> {
            try {
                debugger.checkBreakpoint(loc, newRt());
            } catch (Throwable t) {
                if (error != null)
                    error.set(t);
            }
        });
        thread.start();
        return thread;
    }

    /** 标准 open 流程：返回 sessionId。 */
    String openSession(DebugWebSocketHandler handler, MockWsSession ws, String id) {
        handler.onMessage(req(id, "session.open", "{\"protoVersion\":\"1.0\"}"));
        Map<String, Object> resp = findResponse(ws.snapshot(), id);
        assertTrue(resp != null && resp.containsKey("result"), "session.open 应成功");
        return (String) resultMap(resp).get("sessionId");
    }

    XLangDebugger debuggerOf(String sessionId) {
        return manager.getSession(sessionId).getDebugger();
    }

    static Breakpoint bp(String path, int line) {
        return Breakpoint.build(path, line);
    }
}
