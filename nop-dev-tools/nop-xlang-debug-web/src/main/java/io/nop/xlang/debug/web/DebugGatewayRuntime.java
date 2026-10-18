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
import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.config.IConfigReference;
import io.nop.graphql.core.ws.IWebSocketSession;
import io.nop.xlang.debugger.session.SessionDebugManager;
import io.nop.xlang.debugger.session.XLangDebuggerConfigs;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关运行时装配（plan 2471 Phase 2）：会话管理器、鉴权器、绑定器、租约/宽限/keepalive
 * 配置、定时器（可注入——测试用同步或短周期执行器）。静态单例由 {@link DebugWebServerConfig}
 * 安装、端点适配器（quarkus/spring）经 {@link #singleton()} 取用（Nop IoC 无注解扫描，
 * 照 JsonRpc 端点 BeanContainer 取依赖惯例改为网关静态装配点）。
 */
public class DebugGatewayRuntime {
    static final ThreadFactory TIMER_FACTORY = new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "xlang-debug-web-timer-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    };

    public static final long KEEPALIVE_INTERVAL_MILLIS = 10_000L;

    private static volatile DebugGatewayRuntime singleton;

    private final SessionDebugManager sessionManager;
    private final DebugAuthenticator authenticator;
    private final DebugSessionBinder binder;
    private volatile long leaseTtlMillis;
    private volatile long renewIntervalMillis;
    private volatile long graceMillis;
    /** keepalive ping 周期（D1 §3.3：10s 基线）；0 = 禁用（测试确定性序列断言）。 */
    private volatile long keepaliveIntervalMillis = KEEPALIVE_INTERVAL_MILLIS;
    private volatile int logBatchSize;
    private volatile long logFlushIntervalMillis;
    private volatile boolean whitelistOverridden;
    private volatile String importWhitelistOverride;
    private final ScheduledExecutorService timer;
    private final AtomicLong connectionSeq = new AtomicLong();
    private volatile ReplService replService;
    /** WS 会话创建权限校验器（plan 2478 SEC-05；null = fail-closed 拒绝）。 */
    private volatile IActionAuthChecker authChecker;
    private volatile long watchEvalTimeoutOverride = -1;
    /** 每连接异步出站通道（plan 2479 C-1；键 = session 实例——R1 O-5）。 */
    private final Map<IWebSocketSession, AsyncOutbound> outboundChannels = new ConcurrentHashMap<>();

    public DebugGatewayRuntime(SessionDebugManager sessionManager, IUserContextExtractor extractor) {
        this(sessionManager, extractor, defaultTimer());
    }

    public DebugGatewayRuntime(SessionDebugManager sessionManager, IUserContextExtractor extractor,
                               ScheduledExecutorService timer) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.authenticator = new DebugAuthenticator(Objects.requireNonNull(extractor, "extractor"));
        this.leaseTtlMillis = read(XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_LEASE_TTL);
        this.renewIntervalMillis = read(XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_LEASE_RENEW_INTERVAL);
        this.graceMillis = read(XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_LEASE_GRACE);
        this.logBatchSize = (int) read(XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_LOG_BATCH_SIZE);
        this.logFlushIntervalMillis = read(XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_LOG_FLUSH_INTERVAL);
        this.timer = timer == null ? defaultTimer() : timer;
        this.binder = new DebugSessionBinder(this);
    }

    private static ScheduledExecutorService defaultTimer() {
        return new ScheduledThreadPoolExecutor(1, TIMER_FACTORY);
    }

    private static long read(IConfigReference<Integer> ref) {
        Integer v = ref.get();
        return v == null ? 0L : v;
    }

    /** 端点适配器取用点（DebugWebServerConfig 安装；未启用返回 null——端点关闭连接）。 */
    public static DebugGatewayRuntime singleton() {
        return singleton;
    }

    public static void installSingleton(DebugGatewayRuntime runtime) {
        singleton = runtime;
    }

    /** 创建 handler（端点适配器唯一入口）；握手鉴权失败时 handler 自行 4401 关闭。 */
    public DebugWebSocketHandler createHandler(io.nop.graphql.core.ws.IWebSocketSession session,
                                               String debugToken) {
        return new DebugWebSocketHandler(this, session, debugToken);
    }

    /** 停机关闭定时器（DebugWebServerConfig.destroy；测试结束清理防线程泄漏）。 */
    public void shutdownTimer() {
        timer.shutdownNow();
    }

    /** 测试注入点：禁用（0）或缩短 keepalive 周期（生产常量 10s，D1 §3.3）。 */
    public void setKeepaliveIntervalMillis(long millis) {
        this.keepaliveIntervalMillis = millis;
    }

    /** 测试注入点：缩短租约 TTL（生产读 nop.xlang.debug-web.lease.ttl-ms）。 */
    public void setLeaseTtlMillis(long millis) {
        this.leaseTtlMillis = millis;
    }

    /** 测试注入点：缩短续期间隔（生产读 nop.xlang.debug-web.lease.renew-interval-ms）。 */
    public void setRenewIntervalMillis(long millis) {
        this.renewIntervalMillis = millis;
    }

    /** 测试注入点：缩短断连宽限（生产读 nop.xlang.debug-web.lease.grace-ms）。 */
    public void setGraceMillis(long millis) {
        this.graceMillis = millis;
    }

    /** 测试注入点：日志攒批条数阈值（生产读 nop.xlang.debug-web.log.batch-size）。 */
    public void setLogBatchSize(int size) {
        this.logBatchSize = size;
    }

    /** 测试注入点：日志定时 flush 间隔（生产读 nop.xlang.debug-web.log.flush-interval-ms）。 */
    public void setLogFlushIntervalMillis(long millis) {
        this.logFlushIntervalMillis = millis;
    }

    public int logBatchSize() {
        return logBatchSize;
    }

    /**
     * REPL import 白名单（plan 2478 SEC-10）：显式测试 override（setImportWhitelist，
     * null=清除 override、""=显式空白名单）优先，否则每调用读配置引用——运行期配置
     * 变更即时生效（构造时快照缺陷 remediation）。
     */
    public String importWhitelist() {
        if (whitelistOverridden)
            return importWhitelistOverride;
        String csv = XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_IMPORT_WHITELIST.get();
        return csv == null ? "" : csv;
    }

    public void setImportWhitelist(String whitelist) {
        this.whitelistOverridden = whitelist != null;
        this.importWhitelistOverride = whitelist;
    }

    /** WS 会话创建权限校验器（DebugWebServerConfig 装配；null = session.open 一律拒绝——fail-closed）。 */
    public IActionAuthChecker authChecker() {
        return authChecker;
    }

    public void setAuthChecker(IActionAuthChecker authChecker) {
        this.authChecker = authChecker;
    }

    /** watch 求值 deadline 窗口时长（plan 2478 SEC-07；显式 override 优先，否则每调用读配置，默认 5000ms）。 */
    public long watchEvalTimeoutMillis() {
        if (watchEvalTimeoutOverride >= 0)
            return watchEvalTimeoutOverride;
        Integer v = XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_WATCH_EVAL_TIMEOUT.get();
        return v == null || v <= 0 ? 5000L : v.longValue();
    }

    /** 测试注入点：缩短 watch 求值超时（生产读 nop.xlang.debug-web.watch.eval-timeout-ms）。 */
    public void setWatchEvalTimeoutMillis(long millis) {
        this.watchEvalTimeoutOverride = millis;
    }

    public long logFlushIntervalMillis() {
        return logFlushIntervalMillis;
    }

    String nextConnectionId() {
        return "conn-" + connectionSeq.incrementAndGet();
    }

    DebugSessionBinder binder() {
        return binder;
    }

    public SessionDebugManager sessionManager() {
        return sessionManager;
    }

    public DebugAuthenticator authenticator() {
        return authenticator;
    }

    public DebugSessionBinder getBinder() {
        return binder;
    }

    /** REPL 服务（DebugWebServerConfig 装配；未装配为 null——端点按禁用语义拒绝）。 */
    public ReplService getReplService() {
        return replService;
    }

    public void setReplService(ReplService replService) {
        this.replService = replService;
    }

    public long leaseTtlMillis() {
        return leaseTtlMillis;
    }

    public long renewIntervalMillis() {
        return renewIntervalMillis;
    }

    public long graceMillis() {
        return graceMillis;
    }

    public long keepaliveIntervalMillis() {
        return keepaliveIntervalMillis;
    }

    public ScheduledExecutorService timer() {
        return timer;
    }

    /**
     * 每连接异步出站入队（plan 2479 C-1）：引擎回调/定时器/持锁路径专用——非阻塞，
     * 满则丢帧限频 warn。键 = session 实例；closed 通道静默丢弃。
     */
    public void enqueueOutbound(IWebSocketSession session, String json) {
        if (session == null)
            return;
        outboundChannels.computeIfAbsent(session, AsyncOutbound::new).offer(json);
    }

    /** 移除并关闭出站通道（binder.onSessionDestroyed 移除时调用；onClose 不摘——B-1 裁定）。 */
    public void removeOutbound(IWebSocketSession session) {
        if (session == null)
            return;
        AsyncOutbound channel = outboundChannels.remove(session);
        if (channel != null)
            channel.close();
    }

    /** 认证便捷方法：返回 userId 或 null（未登录）。NopLoginException 由调用方捕获映射 4401。 */
    public String authenticate(String debugToken) {
        IUserContext ctx = authenticator.authenticate(debugToken);
        if (ctx == null)
            return null;
        // userId 为 null 视为未登录（plan 2471 审计 F2：String.valueOf 会产出 "null"
        // 字符串身份，多个无 id 用户将共享 boundUserId）
        return ctx.getUserId();
    }
}
