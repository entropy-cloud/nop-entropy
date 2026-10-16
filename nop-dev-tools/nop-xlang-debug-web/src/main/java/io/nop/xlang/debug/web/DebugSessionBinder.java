/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.ThreadInfo;
import io.nop.graphql.core.ws.IWebSocketSession;
import io.nop.xlang.debugger.XLangDebugger;
import io.nop.xlang.debugger.session.DebugSessionHandle;
import io.nop.xlang.debugger.session.SessionDebugManager;
import io.nop.xlang.debugger.session.SessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_NO_SESSION;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_RATE_LIMITED;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_SESSION_ALREADY_OPEN;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_SESSION_BOUND_ELSEWHERE;

/**
 * 连接↔会话绑定器（D1 §3.4 / D2 §3.3/§3.4/§3.5）：会话创建与销毁路径编排、租约续期
 * 限流（按连接计量、0.8 容差、rebind 首豁免）、断连宽限定时器→gracefulDispose、
 * 销毁 reason 记录（userClosed/graceExpired/serverShutdown）。
 *
 * <p>会话创建键 = {@code userId:connectionId}（D3 §3.2 标记契约；属主校验以
 * {@link #sessionOwners} 登记为准，不依赖键字符串解析）。会话与握手身份绑定——
 * rebind 及一切跨连接会话操作校验属主，违反报 SESSION_BOUND_ELSEWHERE（D1 §3.4
 * 红线：不区分不存在/他属，防会话探测）。
 *
 * <p>并发纪律：本类全部 public 方法在 binder 监视器锁内执行（引擎线程不进本锁；
 * 锁序 binder → manager → debugger.monitorLock → 出站 session 监视器，与引擎
 * 回调链 debugger → 出站 无环）。SessionListener 回调由 manager 保证锁外触发。
 */
public class DebugSessionBinder {
    static final Logger LOG = LoggerFactory.getLogger(DebugSessionBinder.class);

    public static final String REASON_USER_CLOSED = "userClosed";
    public static final String REASON_GRACE_EXPIRED = "graceExpired";
    public static final String REASON_SERVER_SHUTDOWN = "serverShutdown";
    /**
     * D1 §3.5 枚举登记值，一期无发射路径预留（协议违规的处置 = 断连 → 宽限销毁，
     * reason 走 graceExpired；D1 §3.8 4400 关闭连接不销毁会话）。供后续协议级
     * 会话拆除场景（D1 additive 演进）使用。
     */
    public static final String REASON_PROTOCOL_ERROR = "protocolError";

    private final DebugGatewayRuntime runtime;
    private final SessionDebugManager sessionManager;

    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();
    /** sessionId → 绑定该会话的连接 id 集合（多标签页共享，D2 §3.4）。 */
    private final Map<String, Set<String>> sessionConnections = new ConcurrentHashMap<>();
    /** sessionId → 属主 userId（open 时登记，rebind 属主校验依据）。 */
    private final Map<String, String> sessionOwners = new ConcurrentHashMap<>();
    /** sessionId → 销毁 reason（destroy 前登记，onDestroyed 时消费）。 */
    private final Map<String, String> destroyReasons = new ConcurrentHashMap<>();
    /** sessionId → 抑制 sessionClosed 事件（rebind 销毁过渡会话用，D1 §3.9）。 */
    private final Set<String> suppressClosedEvents = ConcurrentHashMap.newKeySet();
    private final Map<String, ScheduledFuture<?>> graceTasks = new ConcurrentHashMap<>();
    private final Map<String, GatewayNotifierBridge> bridges = new ConcurrentHashMap<>();
    /** sessionId → 会话日志攒批管道（plan 2472：binder 按 sessionId 持有，不随 bridge 生命周期）。 */
    private final Map<String, DebugLogPipe> logPipes = new ConcurrentHashMap<>();

    /**
     * 会话创建线程局部暂存：manager.createSession 的 onCreated 回调在同一调用线程
     * 同步触发（SessionDebugManager pendingCreated 机制），据此把出站会话交给回调接线。
     */
    private final ThreadLocal<PendingWire> pendingWire = new ThreadLocal<>();

    private SessionListener listener;

    public DebugSessionBinder(DebugGatewayRuntime runtime) {
        this.runtime = runtime;
        this.sessionManager = runtime.sessionManager();
    }

    /**
     * 注册 SessionListener（网关初始化时调用一次；manager 单 listener 槽位归网关）。
     */
    public synchronized void install() {
        if (listener != null)
            return;
        listener = new XLangDebuggerSessionListener(this);
        sessionManager.setSessionListener(listener);
    }

    public synchronized SessionListener listener() {
        return listener;
    }

    /**
     * session.open：创建会话（键 userId:connectionId）、授予租约（renewLease(ttl)，
     * D2 §3.1）、登记绑定。重复 open（同连接已绑定）→ SESSION_ALREADY_OPEN（随后 4409）。
     */
    public synchronized Binding open(String connectionId, String userId, String protoVersion,
                                     IWebSocketSession wsSession) {
        if (bindings.containsKey(connectionId))
            throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_SESSION_ALREADY_OPEN.getErrorCode(),
                    "connection already bound to session: " + connectionId, null, (short) 4409);

        String sessionId = userId + ":" + connectionId;
        PendingWire wire = new PendingWire(wsSession);
        pendingWire.set(wire);
        DebugSessionHandle handle;
        try {
            handle = sessionManager.createSession(sessionId);
        } finally {
            pendingWire.remove();
        }
        XLangDebugger debugger = handle.getDebugger();
        debugger.renewLease(runtime.leaseTtlMillis());

        Binding binding = new Binding(connectionId, userId, wsSession);
        binding.sessionId = sessionId;
        binding.handle = handle;
        binding.bridge = wire.bridge;
        bindings.put(connectionId, binding);
        sessionOwners.put(sessionId, userId);
        sessionConnections.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(connectionId);
        bridges.put(sessionId, wire.bridge);
        attachLogPipe(sessionId, wire.bridge);
        return binding;
    }

    /** 创建并接线会话日志攒批管道（plan 2472；open 与 rebind 后的新 bridge 共用入口）。 */
    private void attachLogPipe(String sessionId, GatewayNotifierBridge bridge) {
        DebugLogPipe pipe = logPipes.get(sessionId);
        if (pipe == null) {
            pipe = new DebugLogPipe(bridge, runtime.logBatchSize(), runtime.logFlushIntervalMillis(),
                    runtime.timer());
            logPipes.put(sessionId, pipe);
        }
        pipe.attachSink(bridge);
        bridge.attachLogPipe(pipe);
    }

    /**
     * session.rebind：属主校验（D1 §3.4 红线）→ 销毁过渡会话（抑制 sessionClosed，
     * D1 §3.9）→ 新连接出站桥重接 → 取消宽限任务 → 续期豁免标记（D2 §3.1）。
     */
    public synchronized RebindResult rebind(String connectionId, String userId, String targetSessionId) {
        DebugSessionHandle target = sessionManager.getSession(targetSessionId);
        String owner = target == null ? null : sessionOwners.get(targetSessionId);
        if (target == null || target.isDestroyed() || owner == null || !owner.equals(userId))
            throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_SESSION_BOUND_ELSEWHERE.getErrorCode(),
                    "session not rebindable: " + targetSessionId, null, (short) 0);

        // 过渡会话销毁（同连接既有绑定的会话；属主=本用户，静默注销）
        Binding binding = bindings.get(connectionId);
        if (binding != null && !binding.sessionId.equals(targetSessionId))
            destroyTransitional(binding);

        XLangDebugger debugger = target.getDebugger();
        // binding 为 null 时（无过渡会话直接 rebind）出站事件无处投递——该形态仅测试桩使用，
        // 生产流程 session.open 先行，bridge 必有落点；此处仍接线保证引擎回调 NPE 安全
        GatewayNotifierBridge bridge = new GatewayNotifierBridge(
                binding == null ? null : binding.wsSession);
        debugger.setNotifier(bridge);

        if (binding != null) {
            Set<String> conns = sessionConnections.get(binding.sessionId);
            if (conns != null)
                conns.remove(connectionId);
            binding.sessionId = targetSessionId;
            binding.handle = target;
            binding.bridge = bridge;
            binding.renewExempt = true;
            // destroyTransitional 触发的 onSessionDestroyed 同步清理会把指向过渡会话的
            // binding 移出注册表（审计 F3 修复面）；字段重定向后必须重新登记
            bindings.put(connectionId, binding);
        }
        bridges.put(targetSessionId, bridge);
        // plan 2472 审计 F2：rebind 绕过 onCreated——目标会话既有 pipe 显式重接新 bridge
        // （旧 sink 残余经过渡会话 destroyFlush 尽力送达，此处只换出站落点）
        attachLogPipe(targetSessionId, bridge);
        sessionConnections.computeIfAbsent(targetSessionId, k -> ConcurrentHashMap.newKeySet())
                .add(connectionId);
        cancelGraceTask(targetSessionId);

        List<Long> suspendedIds = debugger.getSuspendedThreadIds();
        RebindResult result = new RebindResult();
        result.sessionId = targetSessionId;
        result.breakpoints = new ArrayList<>(debugger.getBreakpoints());
        result.suspendedThreads = new ArrayList<>(debugger.getSuspendedThreads());
        result.expiresAtMillis = debugger.getLeaseDeadlineMillis();
        result.suspendedThreadIds = suspendedIds;
        return result;
    }

    /**
     * lease.renew：按连接限流（阈值 = renewInterval × 0.8，D2 §3.1；不关闭连接），
     * rebind/open 后首个 renew 豁免；推进会话租约时钟并返回 LeaseState。
     */
    public synchronized LeaseStateView renew(String connectionId) {
        Binding binding = requireBinding(connectionId);
        long now = System.currentTimeMillis();
        if (!binding.renewExempt) {
            long minInterval = (long) (runtime.renewIntervalMillis() * 0.8);
            if (minInterval > 0 && now - binding.lastRenewMillis < minInterval)
                throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_RATE_LIMITED.getErrorCode(),
                        "lease.renew too frequent", null, (short) 0);
        }
        binding.renewExempt = false;
        binding.lastRenewMillis = now;
        XLangDebugger debugger = requireDebugger(binding);
        debugger.renewLease(runtime.leaseTtlMillis());
        LeaseStateView view = new LeaseStateView();
        view.expiresAtMillis = debugger.getLeaseDeadlineMillis();
        view.suspendedThreadIds = debugger.getSuspendedThreadIds();
        return view;
    }

    /**
     * session.close（close 语义中止 + 销毁）：destroy reason = userClosed。
     * 调用方（handler）必须先发送 close 的成功响应再调用本方法——onDestroyed 回调
     * 同步触发 evt.sessionClosed，D1 §3.9 要求响应先于事件。
     */
    public synchronized void closeSession(String connectionId) {
        Binding binding = requireBinding(connectionId);
        destroyReasons.put(binding.sessionId, REASON_USER_CLOSED);
        sessionManager.destroySession(binding.handle);
        cleanupBinding(binding);
    }

    /**
     * 连接断开（onClose）：解除绑定；会话再无绑定连接时进入宽限计时
     * （宽限期满 gracefulDispose = resume 语义放行 + 销毁，reason=graceExpired，D2 §3.3）。
     * 已销毁会话（session.close 路径）不再排宽限。
     */
    public synchronized void unbind(String connectionId) {
        Binding binding = bindings.remove(connectionId);
        if (binding == null)
            return;
        Set<String> conns = sessionConnections.get(binding.sessionId);
        if (conns != null) {
            conns.remove(connectionId);
            if (!conns.isEmpty())
                return; // 多标签页：仍有存活绑定连接，会话保持 Active（D2 §3.3）
        }
        DebugSessionHandle handle = sessionManager.getSession(binding.sessionId);
        if (handle == null || handle.isDestroyed())
            return;
        long graceMillis = runtime.graceMillis();
        ScheduledFuture<?> task = runtime.timer().schedule(() -> disposeByGrace(binding.sessionId),
                graceMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        graceTasks.put(binding.sessionId, task);
    }

    /**
     * 宽限期满销毁（宽限期内 rebind 则取消任务，见 {@link #rebind}；重绑竞争失败即空跑）。
     */
    public synchronized void disposeByGrace(String sessionId) {
        graceTasks.remove(sessionId);
        Set<String> conns = sessionConnections.get(sessionId);
        if (conns != null && !conns.isEmpty())
            return; // 宽限窗口内重连（任务取消前的在途竞态）：会话保持 Active
        DebugSessionHandle handle = sessionManager.getSession(sessionId);
        if (handle == null || handle.isDestroyed())
            return;
        LOG.info("nop.xlang.debug-web.grace-expired:sessionId={}", sessionId);
        destroyReasons.put(sessionId, REASON_GRACE_EXPIRED);
        sessionManager.gracefulDispose(handle);
        cleanupSession(sessionId);
    }

    /**
     * 停机清扫（D2 §3.3 DestroyingByClose：等效 close 语义）：全部会话登记
     * serverShutdown 后走 manager.shutdownAll，onDestroyed 逐会话推 evt.sessionClosed。
     */
    public synchronized void shutdown() {
        for (Binding binding : bindings.values())
            destroyReasons.put(binding.sessionId, REASON_SERVER_SHUTDOWN);
        for (String sessionId : new ArrayList<>(sessionOwners.keySet()))
            destroyReasons.putIfAbsent(sessionId, REASON_SERVER_SHUTDOWN);
        cancelAllGraceTasks();
        sessionManager.shutdownAll();
        bindings.clear();
        sessionConnections.clear();
        bridges.clear();
        logPipes.clear();
    }

    public synchronized Binding getBinding(String connectionId) {
        return connectionId == null ? null : bindings.get(connectionId);
    }

    public GatewayNotifierBridge getBridge(String sessionId) {
        return bridges.get(sessionId);
    }

    /** 测试观测面：登记中的会话身份条目数（审计 F3 泄漏回归断言用）。 */
    synchronized int trackedSessionCount() {
        return sessionOwners.size();
    }

    /**
     * REPL execute 属主校验面（plan 2473，D1 §3.4 红线）：会话存在、未销毁且属主匹配
     * 才返回 true——不区分不存在/他属的语义由调用方统一报错承接（防会话探测）。
     */
    public boolean isSessionOwner(String sessionId, String userId) {
        if (sessionId == null || userId == null)
            return false;
        String owner = sessionOwners.get(sessionId);
        return owner != null && owner.equals(userId)
                && sessionConnections.containsKey(sessionId);
    }

    // ===== SessionListener 回调（经 XLangDebuggerSessionListener 委派；manager 锁外触发） =====

    void onSessionCreated(DebugSessionHandle handle, XLangDebugger debugger) {
        PendingWire wire = pendingWire.get();
        if (wire != null) {
            GatewayNotifierBridge bridge = new GatewayNotifierBridge(wire.wsSession);
            debugger.setNotifier(bridge);
            wire.bridge = bridge;
        }
    }

    void onSessionDestroyed(DebugSessionHandle handle, XLangDebugger debugger) {
        String sessionId = handle.getSessionId();
        GatewayNotifierBridge bridge = bridges.get(sessionId);
        bridges.remove(sessionId, bridge);
        cancelGraceTask(sessionId);
        // plan 2472：销毁 flush 前置于 suppress 早退与 sessionClosed——evt.logs 保序
        // 先于终态事件到达（过渡会话残余随旧 bridge 尽力送达）
        DebugLogPipe pipe = logPipes.remove(sessionId);
        if (pipe != null)
            pipe.destroyFlush();
        // plan 2471 审计 F3：suppress（rebind 过渡会话静默注销）路径同样要清理
        // owners/connections 登记面，否则每次带过渡会话的 rebind 泄漏两条 map 条目
        cleanupSession(sessionId);
        if (suppressClosedEvents.remove(sessionId))
            return;
        String reason = destroyReasons.remove(sessionId);
        if (reason == null)
            reason = REASON_USER_CLOSED;
        if (bridge != null)
            bridge.fireSessionClosed(reason);
    }

    // ===== 内部 =====

    /** rebind 成功路径：过渡会话静默注销（不推 sessionClosed，D1 §3.9）。 */
    private void destroyTransitional(Binding binding) {
        suppressClosedEvents.add(binding.sessionId);
        try {
            sessionManager.destroySession(binding.handle);
        } catch (Exception e) {
            // 过渡会话已不存在（宽限任务竞态销毁）：rebind 继续目标会话绑定
            LOG.debug("nop.xlang.debug-web.transitional-dispose-fail:sessionId={}", binding.sessionId, e);
        }
    }

    private void cleanupBinding(Binding binding) {
        bindings.remove(binding.connectionId, binding);
        cleanupSession(binding.sessionId);
    }

    private void cleanupSession(String sessionId) {
        Set<String> conns = sessionConnections.get(sessionId);
        if (conns != null) {
            for (String connId : new HashSet<>(conns)) {
                Binding binding = bindings.get(connId);
                if (binding != null && binding.sessionId.equals(sessionId))
                    bindings.remove(connId);
            }
            sessionConnections.remove(sessionId, conns);
        }
        sessionOwners.remove(sessionId);
    }

    private void cancelGraceTask(String sessionId) {
        ScheduledFuture<?> task = graceTasks.remove(sessionId);
        if (task != null)
            task.cancel(false);
    }

    private void cancelAllGraceTasks() {
        for (ScheduledFuture<?> task : graceTasks.values())
            task.cancel(false);
        graceTasks.clear();
    }

    private Binding requireBinding(String connectionId) {
        Binding binding = getBinding(connectionId);
        if (binding == null)
            throw new DebugProtocolException(-32000, ERR_XLANG_DEBUGGER_NO_SESSION.getErrorCode(),
                    "no session bound to connection: " + connectionId, null, (short) 0);
        return binding;
    }

    private XLangDebugger requireDebugger(Binding binding) {
        XLangDebugger debugger = binding.handle.getDebugger();
        if (debugger == null)
            throw new NopException(ERR_XLANG_DEBUGGER_NO_SESSION).param("sessionId", binding.sessionId);
        return debugger;
    }

    /** open → createSession 同线程回调暂存（见 pendingWire 说明）。 */
    static final class PendingWire {
        final IWebSocketSession wsSession;
        GatewayNotifierBridge bridge;

        PendingWire(IWebSocketSession wsSession) {
            this.wsSession = wsSession;
        }
    }

    /** 连接绑定记录：生命周期仅在本 binder 锁内变更。 */
    public static final class Binding {
        final String connectionId;
        final String userId;
        final IWebSocketSession wsSession;
        volatile String sessionId;
        volatile DebugSessionHandle handle;
        volatile GatewayNotifierBridge bridge;
        long lastRenewMillis;
        boolean renewExempt;

        Binding(String connectionId, String userId, IWebSocketSession wsSession) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.wsSession = wsSession;
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getUserId() {
            return userId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public DebugSessionHandle getHandle() {
            return handle;
        }

        public GatewayNotifierBridge getBridge() {
            return bridge;
        }
    }

    /** D1 §3.4 session.rebind result 载体。 */
    public static final class RebindResult {
        public String sessionId;
        public List<Breakpoint> breakpoints = Collections.emptyList();
        public List<ThreadInfo> suspendedThreads = Collections.emptyList();
        public long expiresAtMillis;
        public List<Long> suspendedThreadIds = Collections.emptyList();

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sessionId", sessionId);
            map.put("breakpoints", breakpoints);
            map.put("suspendedThreads", suspendedThreads);
            map.put("lease", leaseMap(expiresAtMillis, suspendedThreadIds));
            return map;
        }
    }

    /** D1 §3.4 LeaseState = {expiresAtMs, suspendedThreadIds}。 */
    public static final class LeaseStateView {
        public long expiresAtMillis;
        public List<Long> suspendedThreadIds = Collections.emptyList();

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("expiresAtMs", expiresAtMillis);
            map.put("suspendedThreadIds", suspendedThreadIds);
            return map;
        }
    }

    static Map<String, Object> leaseMap(long expiresAtMillis, List<Long> suspendedThreadIds) {
        Map<String, Object> lease = new LinkedHashMap<>();
        lease.put("expiresAtMs", expiresAtMillis);
        lease.put("suspendedThreadIds", suspendedThreadIds);
        return lease;
    }
}
