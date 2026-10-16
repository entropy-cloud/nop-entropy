/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.auth.IUserContext;
import io.nop.xlang.debugger.session.SessionDebugManager;
import io.nop.xlang.debugger.session.XLangDebuggerConfigs;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关运行时装配（plan 2471 Phase 1/2）：会话管理器、鉴权器、租约/宽限配置、
 * 定时器（可注入——测试缩短周期或用同步执行）。
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

    private final SessionDebugManager sessionManager;
    private final DebugAuthenticator authenticator;
    private final long leaseTtlMillis;
    private final long renewIntervalMillis;
    private final long graceMillis;
    private final ScheduledExecutorService timer;

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
        this.timer = timer == null ? defaultTimer() : timer;
    }

    private static ScheduledExecutorService defaultTimer() {
        return new ScheduledThreadPoolExecutor(1, TIMER_FACTORY);
    }

    private static long read(IConfigReference<Integer> ref) {
        Integer v = ref.get();
        return v == null ? 0L : v;
    }

    public SessionDebugManager sessionManager() {
        return sessionManager;
    }

    public DebugAuthenticator authenticator() {
        return authenticator;
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

    public ScheduledExecutorService timer() {
        return timer;
    }

    /** 认证便捷方法：返回 userId 或 null（未登录）。NopLoginException 由调用方捕获映射 4401。 */
    public String authenticate(String debugToken) {
        IUserContext ctx = authenticator.authenticate(debugToken);
        return ctx == null ? null : String.valueOf(ctx.getUserId());
    }
}
