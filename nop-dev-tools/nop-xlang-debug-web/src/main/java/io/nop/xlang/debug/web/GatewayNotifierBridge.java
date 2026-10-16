/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.StackInfo;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.json.JsonTool;
import io.nop.graphql.core.ws.IWebSocketSession;
import io.nop.xlang.debugger.IDebugNotifier;
import io.nop.xlang.debugger.SuspendedThread;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 每会话事件出站桥（D1 §3.5 8 条中 5 条出站 + completed 预留入口；evt.logs/evt.exception
 * 归 item 13 不在本桥）：实现每会话 {@link IDebugNotifier}，把引擎回调转换为
 * {@code evt.*} 无 id notification 经 WS 会话出站。
 *
 * <p>notifyLog/notifyWarn 直通单条 evt.log（攒批管道归 item 13）。evt.completed 不由引擎
 * 产生——预留 {@link #fireCompleted} 发射入口供 item 14/21 调用。
 *
 * <p>线程安全：send 对 session 加锁（JSR-356 basic remote 非并发安全——引擎回调线程、
 * WS 容器线程响应、keepalive 定时器三写者共用同一会话，D2 §3.7 出站锁）。
 */
public class GatewayNotifierBridge implements IDebugNotifier {
    private final IWebSocketSession session;
    private final List<Long> suspendedThreadIds = new CopyOnWriteArrayList<>();

    public GatewayNotifierBridge(IWebSocketSession session) {
        this.session = session;
    }

    /** D1 §3.5：suspend/stepInto/stepOver/stepOut/breakAt 5 回调合一 + reason 字段。 */
    private void fireSuspended(String reason, SuspendedThread thread, SourceLocation loc) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reason", reason);
        long threadId = thread == null ? 0L : thread.getThreadId();
        params.put("threadId", threadId);
        suspendedThreadIds.add(threadId);
        StackInfo stackInfo = thread == null ? null : thread.getStackInfo();
        params.put("stackInfo", stackInfo);
        if (loc != null) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("sourcePath", loc.getPath());
            location.put("line", loc.getLine());
            params.put("location", location);
        }
        sendNotification("evt.suspended", params);
    }

    @Override
    public void notifySuspend(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        fireSuspended("suspend", thread, loc);
    }

    @Override
    public void notifyStepInto(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        fireSuspended("stepInto", thread, loc);
    }

    @Override
    public void notifyStepOver(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        fireSuspended("stepOver", thread, loc);
    }

    @Override
    public void notifyStepOut(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        fireSuspended("stepOut", thread, loc);
    }

    @Override
    public void notifyBreakAt(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        fireSuspended("breakAt", thread, loc);
    }

    /** D1 §3.5：直通单条 evt.log（攒批管道归 item 13）。 */
    @Override
    public void notifyLog(SuspendedThread thread, String sourcePath, int line, String message) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("level", "info");
        params.put("message", message);
        if (sourcePath != null) {
            params.put("sourcePath", sourcePath);
            params.put("line", line);
        }
        if (thread != null)
            params.put("threadId", thread.getThreadId());
        sendNotification("evt.log", params);
    }

    /** notifyWarn 归并为 level=warn 的 evt.log（D1 §3.5 归并裁定）。 */
    @Override
    public void notifyWarn(String message, Object... args) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("level", "warn");
        params.put("message", message);
        sendNotification("evt.log", params);
    }

    @Override
    public void notifyLeaseExpired(List<Long> threadIds) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadIds", threadIds);
        sendNotification("evt.leaseExpired", params);
    }

    @Override
    public void notifyResumed(List<Long> threadIds, String cause) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("threadIds", threadIds);
        params.put("cause", cause);
        sendNotification("evt.resumed", params);
    }

    /** 预留发射入口（plan 2471 M3 裁定）：REPL/被调试脚本完成时由 item 14/21 调用。 */
    public void fireCompleted(boolean ok, String result, String errorCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ok", ok);
        if (ok) {
            params.put("result", result);
        } else {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("message", result == null ? "" : result);
            if (errorCode != null)
                error.put("errorCode", errorCode);
            params.put("error", error);
        }
        sendNotification("evt.completed", params);
    }

    /** 会话销毁终态通知（D2 §3.6：graceExpired 等四值 reason）。 */
    public void fireSessionClosed(String reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reason", reason);
        sendNotification("evt.sessionClosed", params);
    }

    public List<Long> getSuspendedThreadIds() {
        return suspendedThreadIds;
    }

    private void sendNotification(String method, Map<String, Object> params) {
        if (session == null)
            return; // 测试桩/特殊装配形态（rebind 无过渡会话）：无出站落点，事件丢弃
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", "2.0");
        frame.put("method", method);
        frame.put("params", params);
        String json = JsonTool.stringify(frame);
        synchronized (session) {
            if (session.isClosed())
                return;
            try {
                session.sendMessage(json);
            } catch (Exception e) {
                // 发送失败：连接已死，静默（关闭码由容器 onClose 处理）
            }
        }
    }
}
