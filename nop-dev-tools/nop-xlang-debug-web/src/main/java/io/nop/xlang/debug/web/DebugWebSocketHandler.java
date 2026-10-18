/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopLoginException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.DebugValueKey;
import io.nop.api.debugger.DebugVariable;
import io.nop.api.debugger.StackInfo;
import io.nop.api.debugger.ThreadInfo;
import io.nop.core.lang.json.JsonTool;
import io.nop.graphql.core.ws.IWebSocketHandler;
import io.nop.graphql.core.ws.IWebSocketSession;
import io.nop.xlang.debugger.XLangDebugger;
import io.nop.xlang.debugger.session.ReplCancelledError;
import io.nop.xlang.debugger.session.ThreadLocalReplDeadlineChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_NO_SESSION;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_PERMISSION_DENIED;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_PROTOCOL_VERSION_UNSUPPORTED;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_RESULT_TOO_LARGE;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_TOO_MANY_BPS;

/**
 * 调试 WS 网关核心 handler（D1 协议实现，plan 2471 Phase 2）：容器无关（仅依赖
 * {@link IWebSocketSession} 抽象），JSON-RPC 2.0 envelope 编解码、会话/租约/调试器
 * 方法分发、约束强制（D1 §3.1）、鉴权（D1 §3.2 查询参数映射，端点适配器抽参传入）、
 * 关闭码语义（D1 §3.8）、keepalive ping。
 *
 * <p>线程安全：出站统一 {@code synchronized (session)}（JSR-356 basic remote 非并发安全
 * ——WS 容器线程、引擎回调线程、keepalive 定时器三写者，D2 §3.7 出站锁；与
 * {@link GatewayNotifierBridge} 共用 session 监视器互斥）。onMessage 不阻塞容器线程
 * （waitSuspended 不暴露为调试 method，D1 §3.3）。
 *
 * <p>线上 errorCode 承载平台点分码（{@link NopException#getErrorCode()}，与
 * ErrorBean.errorCode 同构）；D1 §3.7 表中 ERR_XLANG_DEBUGGER_* 为 Java 常量名，
 * 与 XLangErrors 登记的点分码一一对应。
 */
public class DebugWebSocketHandler implements IWebSocketHandler {
    static final Logger LOG = LoggerFactory.getLogger(DebugWebSocketHandler.class);

    public static final String JSONRPC_VERSION = "2.0";
    /** D1 §3.6：当前支持的协议主版本（additive-only 演进，任意 minor 可接受并回显）。 */
    public static final String SUPPORTED_PROTO_MAJOR = "1";

    // D1 §3.1 约束基线（调整须改 01-ws-protocol.md §3.1 表）
    public static final int MAX_INBOUND_FRAME_BYTES = 256 * 1024;
    public static final int MAX_EXPR_LENGTH = 8 * 1024;
    public static final int MAX_BREAKPOINTS = 500;
    public static final int MAX_EXPAND_KEYS = 50;
    public static final int MAX_OUTBOUND_FRAME_BYTES = 1024 * 1024;
    public static final int MAX_VARIABLES_PER_RESPONSE = 1000;
    /** 入站协议违规（无 id / 超大帧）计数阈值，超阈 4400（D1 §3.7：阈值归实现常量）。 */
    public static final int MAX_PROTOCOL_VIOLATIONS = 10;

    static final String SESSION_NS = "session.";
    static final String LEASE_NS = "lease.";
    static final String DEBUGGER_NS = "debugger.";
    /** D6 §3.4 权限矩阵 WS 行：会话创建权限码（plan 2478 SEC-05 落地）。 */
    static final String DEBUG_SESSION_PERMISSION = "nop/xlang/debug/DebugSession";
    static final String METHOD_PING = "ping";
    static final String METHOD_PONG = "pong";
    static final String METHOD_TOKEN_REFRESH = "tokenRefresh";

    private final DebugGatewayRuntime runtime;
    private final DebugSessionBinder binder;
    private final IWebSocketSession session;
    private final String connectionId;

    private volatile String boundUserId;
    private volatile boolean authenticated;
    private volatile boolean closed;
    private int violations;
    private Future<?> keepaliveTask;

    public DebugWebSocketHandler(DebugGatewayRuntime runtime, IWebSocketSession session, String debugToken) {
        this.runtime = runtime;
        this.binder = runtime.binder();
        this.session = session;
        this.connectionId = runtime.nextConnectionId();

        String userId;
        try {
            userId = runtime.authenticate(debugToken);
        } catch (NopLoginException e) {
            LOG.info("nop.xlang.debug-web.auth-failed:conn={}", connectionId);
            userId = null;
        }
        if (userId == null) {
            close((short) 4401, "Authentication required");
            return;
        }
        this.boundUserId = userId;
        this.authenticated = true;
        long keepaliveMillis = runtime.keepaliveIntervalMillis();
        if (keepaliveMillis > 0) {
            keepaliveTask = runtime.timer().scheduleWithFixedDelay(this::sendKeepAlive,
                    keepaliveMillis, keepaliveMillis, TimeUnit.MILLISECONDS);
        }
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getBoundUserId() {
        return boundUserId;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void onMessage(String text) {
        if (!authenticated || closed || text == null || text.isEmpty())
            return;

        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_INBOUND_FRAME_BYTES) {
            // D1 §3.1：超大帧 error -32600 + 计数（帧不可解析，id 未知以 null 应答）
            sendError(null, -32600, "inbound frame exceeds " + MAX_INBOUND_FRAME_BYTES + " bytes", null, null);
            countViolationAndMaybeClose();
            return;
        }

        Map<String, Object> msg;
        try {
            msg = JsonTool.parseMap(text);
        } catch (Exception e) {
            LOG.info("nop.xlang.debug-web.parse-fail:conn={}", connectionId);
            close((short) 4400, "parse error");
            return;
        }

        Object methodObj = msg.get("method");
        if (!(methodObj instanceof String) || ((String) methodObj).isEmpty()) {
            recordViolationAndRespond();
            return;
        }
        String method = (String) methodObj;
        Object idObj = msg.get("id");
        String id = idObj == null ? null : String.valueOf(idObj);

        // D1 §3.3：pong 豁免（唯一合法 notification），其余入站一律带 id
        if (METHOD_PONG.equals(method))
            return;
        if (id == null) {
            recordViolationAndRespond();
            return;
        }

        try {
            dispatch(method, id, msg.get("params"));
        } catch (DebugProtocolException e) {
            sendError(id, e.getJsonRpcCode(), e.getProtocolMessage(), e.getErrorCode(), e.getData());
            if (e.getCloseCode() != 0)
                close(e.getCloseCode(), e.getProtocolMessage());
        } catch (NopException e) {
            sendError(id, -32000,
                    e.getDescription() != null ? e.getDescription() : e.getErrorCode(),
                    e.getErrorCode(), e.getParams());
        } catch (IllegalArgumentException e) {
            sendError(id, -32602, String.valueOf(e), null, null);
        } catch (Exception e) {
            LOG.error("nop.xlang.debug-web.dispatch-fail:conn={},method={}", connectionId, method, e);
            sendError(id, -32603, "internal error", null, null);
        }
    }

    @Override
    public void onThrowable(Throwable t) {
        LOG.warn("nop.xlang.debug-web.handler-error:conn={}", connectionId, t);
    }

    @Override
    public void onClose() {
        if (keepaliveTask != null)
            keepaliveTask.cancel(false);
        closed = true;
        // plan 2479 B-1 裁定：此处不摘出站通道——断连后宽限路径事件（leaseExpired/
        // logs/sessionClosed）仍需可达（D2 §3.6 序列证据）；通道生命周期终于
        // binder.onSessionDestroyed 的 removeOutbound（drain 时按 isClosed 丢弃死连接帧）
        try {
            binder.unbind(connectionId);
        } catch (Exception e) {
            LOG.error("nop.xlang.debug-web.unbind-fail:conn={}", connectionId, e);
        }
    }

    // ===== 分发 =====

    private void dispatch(String method, String id, Object params) {
        if (METHOD_PING.equals(method)) {
            // 保留方法（基座语义）：带 id 的 ping 回 pong 确认
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pong", true);
            sendResult(id, result);
            return;
        }
        if (METHOD_TOKEN_REFRESH.equals(method)) {
            handleTokenRefresh(id, params);
            return;
        }
        if ("unsubscribe".equals(method)) {
            // D1 §3.3：调试协议无订阅概念，不采纳
            throw new DebugProtocolException(-32601, "unknown method: unsubscribe");
        }
        if (method.startsWith(SESSION_NS)) {
            switch (method) {
                case "session.open":
                    handleSessionOpen(id, params);
                    return;
                case "session.rebind":
                    requireParams(params);
                    String targetId = stringParam(asMap(params), "sessionId");
                    DebugSessionBinder.RebindResult result =
                            binder.rebind(connectionId, boundUserId, targetId);
                    sendResult(id, result.toMap());
                    return;
                case "session.close":
                    // D1 §3.9 顺序：close 响应先于 evt.sessionClosed（onDestroyed 同步触发）
                    sendResult(id, emptyResult());
                    if (binder.getBinding(connectionId) != null)
                        binder.closeSession(connectionId);
                    return;
                default:
                    throw unknownMethod(method);
            }
        }
        if (method.startsWith(LEASE_NS)) {
            if ("lease.renew".equals(method)) {
                DebugSessionBinder.LeaseStateView state = binder.renew(connectionId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("lease", state.toMap());
                sendResult(id, result);
                return;
            }
            throw unknownMethod(method);
        }
        if (method.startsWith(DEBUGGER_NS)) {
            handleDebuggerMethod(method.substring(DEBUGGER_NS.length()), id, params);
            return;
        }
        throw unknownMethod(method);
    }

    private void handleSessionOpen(String id, Object params) {
        requireParams(params);
        Map<String, Object> p = asMap(params);
        String protoVersion = stringParam(p, "protoVersion");
        if (!isSupportedProtoVersion(protoVersion)) {
            // D1 §3.6/§3.7：主版本不支持 → 错误响应 + 4400 关闭
            throw new DebugProtocolException(-32000,
                    ERR_XLANG_DEBUGGER_PROTOCOL_VERSION_UNSUPPORTED.getErrorCode(),
                    "unsupported protoVersion: " + protoVersion, protoVersion, (short) 4400);
        }
        // D6 §3.4 权限矩阵 WS 行（plan 2478 SEC-05）：会话创建须持调试权限码；
        // checker 缺失 = fail-closed 拒绝。拒绝语义：-32000 错误响应 + 4403 关闭
        //（前端既有 onclose 4403 终态分支天然兼容，D1 additive 登记见 01-ws-protocol.md）
        IActionAuthChecker checker = runtime.authChecker();
        if (checker == null || !checker.isPermitted(DEBUG_SESSION_PERMISSION, null)) {
            throw new DebugProtocolException(-32000,
                    ERR_XLANG_DEBUGGER_PERMISSION_DENIED.getErrorCode(),
                    "debug session permission denied", null, (short) 4403);
        }
        DebugSessionBinder.Binding binding = binder.open(connectionId, boundUserId, protoVersion, session);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", binding.getSessionId());
        result.put("protoVersion", protoVersion);
        Map<String, Object> lease = new LinkedHashMap<>();
        lease.put("ttlMs", runtime.leaseTtlMillis());
        lease.put("renewIntervalMs", runtime.renewIntervalMillis());
        result.put("lease", lease);
        sendResult(id, result);
    }

    private void handleTokenRefresh(String id, Object params) {
        requireParams(params);
        Map<String, Object> p = asMap(params);
        Object accessToken = p.get("accessToken");
        if (!(accessToken instanceof String) || ((String) accessToken).isEmpty())
            throw DebugProtocolException.invalidParams("tokenRefresh requires accessToken");
        IUserContext ctx;
        try {
            ctx = runtime.authenticator().refresh((String) accessToken);
        } catch (NopLoginException e) {
            close((short) 4401, "Token refresh failed");
            return;
        }
        if (ctx == null) {
            close((short) 4401, "Token refresh failed");
            return;
        }
        String newUserId = ctx.getUserId();
        if (newUserId != null && !newUserId.equals(boundUserId)) {
            LOG.warn("nop.xlang.debug-web.token-refresh-user-mismatch:conn={},bound={},new={}",
                    connectionId, boundUserId, newUserId);
            close((short) 4403, "Token user mismatch, please re-login");
            return;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refreshed", true);
        sendResult(id, result);
    }

    /** D1 §3.3：IDebugger 16 个自有方法 + IBreakpointManager 6 方法机械映射
     * （D1 表登记 24 = 22 暴露 + waitSuspended/close 2 个不暴露）。 */
    private void handleDebuggerMethod(String name, String id, Object params) {
        DebugSessionBinder.Binding binding = binder.getBinding(connectionId);
        if (binding == null || binding.getHandle().isDestroyed())
            throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_NO_SESSION.getErrorCode(),
                    "no session bound to connection", null, (short) 0);
        XLangDebugger debugger = binding.getHandle().getDebugger();
        if (debugger == null)
            throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_NO_SESSION.getErrorCode(),
                    "session has no debugger instance", null, (short) 0);

        Map<String, Object> p = params == null ? null : asMap(params);
        switch (name) {
            // 断点管理（IBreakpointManager 6/6）
            case "addBreakpoint": {
                Breakpoint bp = breakpointParam(p);
                if (debugger.getBreakpoints().size() >= MAX_BREAKPOINTS)
                    throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_TOO_MANY_BPS.getErrorCode(),
                            "breakpoint count exceeds " + MAX_BREAKPOINTS, null, (short) 0);
                debugger.addBreakpoint(bp);
                sendResult(id, emptyResult());
                return;
            }
            case "removeBreakpoint":
                debugger.removeBreakpoint(breakpointParam(p));
                sendResult(id, emptyResult());
                return;
            case "setBreakpoints": {
                List<Breakpoint> bps = breakpointListParam(p);
                if (bps.size() > MAX_BREAKPOINTS)
                    throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_TOO_MANY_BPS.getErrorCode(),
                            "breakpoint count exceeds " + MAX_BREAKPOINTS, null, (short) 0);
                debugger.setBreakpoints(bps);
                sendResult(id, emptyResult());
                return;
            }
            case "getBreakpoints": {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("bps", debugger.getBreakpoints());
                sendResult(id, result);
                return;
            }
            case "clearBreakpoints":
                debugger.clearBreakpoints();
                sendResult(id, emptyResult());
                return;
            case "getBreakpointAt": {
                requireParams(p);
                Breakpoint bp = debugger.getBreakpointAt(locationParam(p));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("bp", bp);
                sendResult(id, result);
                return;
            }

            // 控制与状态（IDebugger）
            case "stepInto":
                debugger.stepInto();
                sendResult(id, emptyResult());
                return;
            case "stepOver":
                debugger.stepOver();
                sendResult(id, emptyResult());
                return;
            case "stepOut":
                debugger.stepOut();
                sendResult(id, emptyResult());
                return;
            case "suspend":
                debugger.suspend();
                sendResult(id, emptyResult());
                return;
            case "resume":
                debugger.resume();
                sendResult(id, emptyResult());
                return;
            case "muteBreakpoints":
                requireParams(p);
                debugger.muteBreakpoints(booleanParam(p, "muted"));
                sendResult(id, emptyResult());
                return;
            case "updateBreakpoints": {
                requireParams(p);
                List<Breakpoint> bps = breakpointListParam(p);
                boolean muted = booleanParam(p, "muted");
                if (bps.size() > MAX_BREAKPOINTS)
                    throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_TOO_MANY_BPS.getErrorCode(),
                            "breakpoint count exceeds " + MAX_BREAKPOINTS, null, (short) 0);
                debugger.updateBreakpoints(bps, muted);
                sendResult(id, emptyResult());
                return;
            }
            case "isSuspended": {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("suspended", debugger.isSuspended());
                sendResult(id, result);
                return;
            }
            case "isBreakpointsMuted": {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("muted", debugger.isBreakpointsMuted());
                sendResult(id, result);
                return;
            }
            case "getSuspendedThreads": {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("threads", debugger.getSuspendedThreads());
                sendResult(id, result);
                return;
            }
            case "runToPosition":
                debugger.runToPosition(breakpointParam(p));
                sendResult(id, emptyResult());
                return;
            case "getExprValue": {
                requireParams(p);
                String expr = exprParam(p);
                long threadId = longParam(p, "threadId");
                requireSuspended(debugger, threadId);
                // plan 2478 SEC-07：watch 求值经完整沙箱校验（与 REPL 同一禁用清单——防
                // inject/import 等经 watch 通道绕过）+ deadline 窗口（超时经取消通道打断）
                validateWatchExpr(expr);
                DebugVariable v = evalWithDeadline(debugger,
                        () -> debugger.getExprValue(threadId, intParam(p, "frameIndex"), expr));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("v", v);
                sendResult(id, result);
                return;
            }
            case "expandExprValue": {
                requireParams(p);
                String expr = exprParam(p);
                Object keysObj = p.get("keys");
                if (!(keysObj instanceof List))
                    throw DebugProtocolException.invalidParams("expandExprValue requires keys array");
                List<?> rawKeys = (List<?>) keysObj;
                if (rawKeys.size() > MAX_EXPAND_KEYS)
                    throw DebugProtocolException.invalidParams(
                            "keys array exceeds " + MAX_EXPAND_KEYS);
                List<DebugValueKey> keys = new ArrayList<>(rawKeys.size());
                for (Object key : rawKeys)
                    keys.add((DebugValueKey) JsonTool.jsonObjectToBean(key, DebugValueKey.class));
                long expandThreadId = longParam(p, "threadId");
                requireSuspended(debugger, expandThreadId);
                validateWatchExpr(expr);
                List<DebugVariable> vars = evalWithDeadline(debugger,
                        () -> debugger.expandExprValue(expandThreadId, intParam(p, "frameIndex"), expr, keys));
                sendResult(id, varsResult(vars));
                return;
            }
            case "getStackInfo": {
                requireParams(p);
                long stackThreadId = longParam(p, "threadId");
                requireSuspended(debugger, stackThreadId);
                StackInfo stackInfo = debugger.getStackInfo(stackThreadId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("stackInfo", stackInfo);
                sendResult(id, result);
                return;
            }
            case "getScopeVariables": {
                requireParams(p);
                long scopeThreadId = longParam(p, "threadId");
                requireSuspended(debugger, scopeThreadId);
                List<DebugVariable> vars = debugger.getScopeVariables(scopeThreadId);
                sendResult(id, varsResult(vars));
                return;
            }
            case "getFrameVariables": {
                requireParams(p);
                long frameThreadId = longParam(p, "threadId");
                requireSuspended(debugger, frameThreadId);
                List<DebugVariable> vars = debugger.getFrameVariables(frameThreadId,
                        intParam(p, "frameIndex"));
                sendResult(id, varsResult(vars));
                return;
            }
            default:
                throw unknownMethod(DEBUGGER_NS + name);
        }
    }

    // ===== 参数抽取 =====

    private Map<String, Object> asMap(Object params) {
        if (params instanceof Map)
            return (Map<String, Object>) params;
        throw DebugProtocolException.invalidParams("params must be an object");
    }

    private void requireParams(Object params) {
        if (params == null)
            throw DebugProtocolException.invalidParams("missing params");
    }

    private void requireParams(Map<String, Object> p) {
        if (p == null)
            throw DebugProtocolException.invalidParams("missing params");
    }

    private String stringParam(Map<String, Object> p, String name) {
        Object v = p.get(name);
        if (!(v instanceof String) || ((String) v).isEmpty())
            throw DebugProtocolException.invalidParams("missing string param: " + name);
        return (String) v;
    }

    private long longParam(Map<String, Object> p, String name) {
        Object v = p.get(name);
        if (v instanceof Number)
            return ((Number) v).longValue();
        throw DebugProtocolException.invalidParams("missing numeric param: " + name);
    }

    private int intParam(Map<String, Object> p, String name) {
        return (int) longParam(p, name);
    }

    private boolean booleanParam(Map<String, Object> p, String name) {
        Object v = p.get(name);
        if (v instanceof Boolean)
            return (Boolean) v;
        throw DebugProtocolException.invalidParams("missing boolean param: " + name);
    }

    private String exprParam(Map<String, Object> p) {
        String expr = stringParam(p, "expr");
        if (expr.length() > MAX_EXPR_LENGTH)
            throw DebugProtocolException.invalidParams(
                    "expr exceeds " + MAX_EXPR_LENGTH + " chars");
        return expr;
    }

    private Breakpoint breakpointParam(Map<String, Object> p) {
        requireParams(p);
        Object bpObj = p.get("bp");
        if (bpObj == null)
            throw DebugProtocolException.invalidParams("missing param: bp");
        return (Breakpoint) JsonTool.jsonObjectToBean(bpObj, Breakpoint.class);
    }

    private List<Breakpoint> breakpointListParam(Map<String, Object> p) {
        requireParams(p);
        Object bpsObj = p.get("bps");
        if (!(bpsObj instanceof List))
            throw DebugProtocolException.invalidParams("missing bps array");
        List<?> raw = (List<?>) bpsObj;
        List<Breakpoint> bps = new ArrayList<>(raw.size());
        for (Object bp : raw)
            bps.add((Breakpoint) JsonTool.jsonObjectToBean(bp, Breakpoint.class));
        return bps;
    }

    private SourceLocation locationParam(Map<String, Object> p) {
        Object locObj = p.get("loc");
        if (!(locObj instanceof Map))
            throw DebugProtocolException.invalidParams("missing param: loc");
        Map<String, Object> loc = asMap(locObj);
        Object path = loc.get("path");
        if (path == null)
            path = loc.get("sourcePath");
        Object line = loc.get("line");
        if (!(path instanceof String) || !(line instanceof Number))
            throw DebugProtocolException.invalidParams("loc requires path and line");
        return SourceLocation.fromLine((String) path, ((Number) line).intValue());
    }

    /**
     * D1 §3.7：对未挂起线程取栈/求值/取变量 → THREAD_NOT_SUSPENDED。引擎对不存在线程
     * 返回 null/空表（无法区分"空结果"），网关侧以挂起集合预检承接该错误语义；
     * 预检与调用间线程恢复的竞态由引擎兜底（plan 2479 C-5：引擎侧锁内 isSuspended
     * 复核——放行瞬态/已放行条目读取抛 THREAD_NOT_SUSPENDED，取代本注释原"良性竞态
     * 空结果"豁免）。
     */
    private void requireSuspended(XLangDebugger debugger, long threadId) {
        if (!debugger.getSuspendedThreadIds().contains(threadId))
            throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED.getErrorCode(),
                    "no suspended thread " + threadId, null, (short) 0);
    }

    /**
     * watch 求值表达式沙箱校验（plan 2478 SEC-07）：与 REPL 同一校验器/同一禁用清单
     * （防 inject/loadDslModel/import/正则字面量等经 watch 通道绕过 REPL 编译面禁用）。
     * 违例按 -32602 invalidParams 拒绝（表达式本身非法）。
     */
    private void validateWatchExpr(String expr) {
        try {
            new ReplSourceValidator().validate(expr, SourceLocation.fromLine("watch.xpl", 1),
                    whitelistForWatch());
        } catch (ReplAccessException e) {
            throw DebugProtocolException.invalidParams("watch expression rejected: " + e.getResponseMessage());
        }
    }

    private java.util.Set<String> whitelistForWatch() {
        String csv = runtime.importWhitelist();
        if (csv == null || csv.isEmpty())
            return java.util.Set.of();
        return java.util.Set.of(csv.trim().split("\\s*,\\s*"));
    }

    /**
     * watch 求值 deadline 窗口（plan 2478 SEC-07）：begin/finally end 包住求值；
     * {@link ReplCancelledError} 是 Error 系（dispatch 的 catch(Exception) 链接不住），
     * 必须在此显式捕获映射 -32000 错误响应，否则超时会穿透 onMessage。打断机制 =
     * 引擎 eval 入口检查 + 函数调用边界经全局执行器 per-entry 检查双层。
     */
    private <T> T evalWithDeadline(XLangDebugger debugger, Supplier<T> eval) {
        ThreadLocalReplDeadlineChannel channel = ThreadLocalReplDeadlineChannel.instance();
        channel.begin(System.currentTimeMillis() + runtime.watchEvalTimeoutMillis());
        try {
            return eval.get();
        } catch (ReplCancelledError e) {
            throw new DebugProtocolException(-32000, e.getErrorCode(), e.getErrorMessage(), null, (short) 0);
        } finally {
            channel.end();
        }
    }

    /** D1 §3.1：出站 DebugVariable 列表截断至 1000 条 + truncated 标记。 */
    private Map<String, Object> varsResult(List<DebugVariable> vars) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (vars != null && vars.size() > MAX_VARIABLES_PER_RESPONSE) {
            result.put("vars", new ArrayList<>(vars.subList(0, MAX_VARIABLES_PER_RESPONSE)));
            result.put("truncated", true);
        } else {
            result.put("vars", vars);
        }
        return result;
    }

    private DebugProtocolException unknownMethod(String method) {
        return new DebugProtocolException(-32601, "unknown method: " + method);
    }

    private Map<String, Object> emptyResult() {
        return new LinkedHashMap<>();
    }

    private boolean isSupportedProtoVersion(String protoVersion) {
        return protoVersion.equals(SUPPORTED_PROTO_MAJOR)
                || (protoVersion.startsWith(SUPPORTED_PROTO_MAJOR + "."));
    }

    // ===== 出站 =====

    private void recordViolationAndRespond() {
        sendError(null, -32600, "invalid request: id required (except pong)", null, null);
        countViolationAndMaybeClose();
    }

    private void countViolationAndMaybeClose() {
        violations++;
        if (violations > MAX_PROTOCOL_VIOLATIONS)
            close((short) 4400, "too many protocol violations");
    }

    private void sendResult(String id, Object result) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", JSONRPC_VERSION);
        frame.put("id", id);
        frame.put("result", result);
        sendFrame(id, frame);
    }

    private void sendError(String id, int code, String message, String errorCode, Object data) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (errorCode != null)
            error.put("errorCode", errorCode);
        if (data != null)
            error.put("data", data);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", JSONRPC_VERSION);
        frame.put("id", id);
        frame.put("error", error);
        sendFrame(id, frame);
    }

    /** 可覆盖的出站帧上限（生产 = MAX_OUTBOUND_FRAME_BYTES；测试降低以触发 RESULT_TOO_LARGE 路径）。 */
    protected int maxOutboundFrameBytes() {
        return MAX_OUTBOUND_FRAME_BYTES;
    }

    private void sendFrame(String id, Map<String, Object> frame) {
        String json = JsonTool.stringify(frame);
        int limit = maxOutboundFrameBytes();
        if (json.getBytes(StandardCharsets.UTF_8).length > limit) {
            if (id == null)
                return; // 事件帧超限：丢弃（best-effort 推送，D1 §3.5）
            // 响应帧超限：改发 RESULT_TOO_LARGE 错误（D1 §3.1）
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", -32000);
            error.put("message", "outbound frame exceeds " + limit + " bytes");
            error.put("errorCode", ERR_XLANG_DEBUGGER_RESULT_TOO_LARGE.getErrorCode());
            Map<String, Object> errorFrame = new LinkedHashMap<>();
            errorFrame.put("jsonrpc", JSONRPC_VERSION);
            errorFrame.put("id", id);
            errorFrame.put("error", error);
            sendText(JsonTool.stringify(errorFrame));
            return;
        }
        sendText(json);
    }

    private void sendText(String json) {
        synchronized (session) {
            if (session.isClosed())
                return;
            try {
                session.sendMessage(json);
            } catch (Exception e) {
                LOG.warn("nop.xlang.debug-web.send-fail:conn={}", connectionId, e);
            }
        }
    }

    private void sendKeepAlive() {
        if (closed)
            return;
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", JSONRPC_VERSION);
        frame.put("method", METHOD_PING);
        // plan 2479 C-1：keepalive 经每连接队列出站——共享 timer 上零 WS IO（停滞客户端
        // 只阻塞其专属 drain 线程，不冻结其他连接的 ping/宽限销毁/日志 flush）
        runtime.enqueueOutbound(session, JsonTool.stringify(frame));
    }

    private void close(short code, String reason) {
        closed = true;
        try {
            session.close(code, reason);
        } catch (Exception e) {
            LOG.warn("nop.xlang.debug-web.close-fail:conn={}", connectionId, e);
        }
    }
}
