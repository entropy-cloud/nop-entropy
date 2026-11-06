/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.StackInfo;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.json.JsonTool;
import io.nop.graphql.core.ws.IWebSocketSession;
import io.nop.xlang.debugger.IDebugNotifier;
import io.nop.xlang.debugger.SuspendedThread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 每会话事件出站桥（D1 §3.5 8 条中 5 条出站 + completed 预留入口；evt.logs/evt.exception
 * 归 item 13 不在本桥）：实现每会话 {@link IDebugNotifier}，把引擎回调转换为
 * {@code evt.*} 无 id notification 出站。
 *
 * <p>notifyLog/notifyWarn 直通单条 evt.log（攒批管道归 item 13）。evt.completed 不由引擎
 * 产生——预留 {@link #fireCompleted} 发射入口供 item 14/21 调用。
 *
 * <p>线程安全（plan 2479 修订，item 24 审计 C-1/C-3 remediation）：出站经
 * {@link OutboundSink}——生产装配 = runtime 队列化入队（每连接
 * {@link AsyncOutbound}，引擎回调线程 / keepalive 定时器 / 持 B·M 锁路径
 * 零 WS IO）；目标集合化（Set 去重）支持多标签页 fan-out：rebind 复用既有 bridge
 * 追加目标，废除 last-rebind-wins 单落点（C-3 / 前端审计 F-7 销案，D2 §3.7 登记）。
 * 测试直构形态（单目标 + 缺省同步直发 sink）保持既有语义。
 */
public class GatewayNotifierBridge implements IDebugNotifier {
    /** 出站落点：生产 = runtime 队列化入队；缺省 = 同步直发（测试直构形态）。 */
    public interface OutboundSink {
        void send(IWebSocketSession target, String json);
    }

    private final Set<IWebSocketSession> targets = new CopyOnWriteArraySet<>();
    private final OutboundSink sink;
    /** 会话日志攒批管道（binder 按 sessionId 接线；null = 直发单条 evt.log 兜底）。 */
    private volatile DebugLogPipe logPipe;

    /** 测试直构形态：单目标 + 同步直发。 */
    public GatewayNotifierBridge(IWebSocketSession session) {
        this(session, null);
    }

    public GatewayNotifierBridge(IWebSocketSession session, OutboundSink sink) {
        if (session != null)
            this.targets.add(session);
        this.sink = sink == null ? new DirectOutboundSink() : sink;
    }

    /** 追加出站目标（binder rebind 复用 bridge 时调用；Set 去重——自 rebind 不双发）。 */
    public void addTarget(IWebSocketSession session) {
        if (session != null)
            targets.add(session);
    }

    /** 摘除出站目标（binder unbind/cleanup 时调用）。 */
    public void removeTarget(IWebSocketSession session) {
        if (session != null)
            targets.remove(session);
    }

    /** 当前出站目标快照（binder onSessionDestroyed 移除出站通道时遍历用）。 */
    public Set<IWebSocketSession> getTargets() {
        return targets;
    }

    /** 接线日志攒批管道（binder onCreated / rebind 重接时调用）。 */
    public void attachLogPipe(DebugLogPipe pipe) {
        this.logPipe = pipe;
    }

    /** D1 §3.5：suspend/stepInto/stepOver/stepOut/breakAt 5 回调合一 + reason 字段。 */
    private void fireSuspended(String reason, SuspendedThread thread, SourceLocation loc) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reason", reason);
        long threadId = thread == null ? 0L : thread.getThreadId();
        params.put("threadId", threadId);
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

    /** D1 §3.5：组装 evt.log entry（item 13 补 timestamp）→ 攒批管道；无 pipe 时直发单条。 */
    @Override
    public void notifyLog(SuspendedThread thread, String sourcePath, int line, String message) {
        Map<String, Object> entry = buildLogEntry("info", message, sourcePath, line,
                thread == null ? null : thread.getThreadId());
        routeLog(entry);
    }

    /** notifyWarn 归并为 level=warn 的 evt.log entry（D1 §3.5 归并裁定）。 */
    @Override
    public void notifyWarn(String message, Object... args) {
        routeLog(buildLogEntry("warn", message, null, 0, null));
    }

    private Map<String, Object> buildLogEntry(String level, String message, String sourcePath,
                                              int line, Long threadId) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("level", level);
        entry.put("message", message);
        // D1 §3.5 evt.log schema：timestamp 必带（item 13 补齐）
        entry.put("timestamp", CoreMetrics.currentTimeMillis());
        if (sourcePath != null) {
            entry.put("sourcePath", sourcePath);
            entry.put("line", line);
        }
        if (threadId != null)
            entry.put("threadId", threadId);
        return entry;
    }

    private void routeLog(Map<String, Object> entry) {
        DebugLogPipe pipe = logPipe;
        if (pipe != null)
            pipe.enqueue(entry);
        else
            sendNotification("evt.log", entry);
    }

    /** evt.logs 攒批出站（pipe flush 调用；单帧全序与事件帧经每连接队列保序）。 */
    public void sendLogs(List<Map<String, Object>> entries) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("entries", entries);
        sendNotification("evt.logs", params);
    }

    /** D1 §3.5 evt.exception（item 13 受控扩展产生源）：低频高价值直发不攒批。 */
    @Override
    public void notifyException(String sourcePath, int line, String message, String errorCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message", message == null ? "" : message);
        if (errorCode != null)
            params.put("errorCode", errorCode);
        if (sourcePath != null && line > 0) {
            params.put("sourcePath", sourcePath);
            params.put("line", line);
        }
        sendNotification("evt.exception", params);
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

    /** 会话销毁终态通知（D2 §3.6：graceExpired 等四值 reason）。fan-out 广播到全部目标。 */
    public void fireSessionClosed(String reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reason", reason);
        sendNotification("evt.sessionClosed", params);
    }

    private void sendNotification(String method, Map<String, Object> params) {
        if (targets.isEmpty())
            return; // 测试桩/特殊装配形态（rebind 无过渡会话）：无出站落点，事件丢弃
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", "2.0");
        frame.put("method", method);
        frame.put("params", params);
        String json = JsonTool.stringify(frame);
        for (IWebSocketSession target : targets) {
            // 引擎回调/定时器/持锁路径零 WS IO：生产 sink（队列化入队）非阻塞；
            // 同步直发 sink（测试直构）仍带锁——行为与旧单目标形态一致
            sink.send(target, json);
        }
    }

    /** 缺省同步直发（测试直构形态；持锁语义与旧版 sendNotification 一致）。 */
    static final class DirectOutboundSink implements OutboundSink {
        @Override
        public void send(IWebSocketSession target, String json) {
            synchronized (target) {
                if (target.isClosed())
                    return;
                try {
                    target.sendMessage(json);
                } catch (Exception e) {
                    // 发送失败：连接已死，静默（关闭码由容器 onClose 处理）
                }
            }
        }
    }
}
