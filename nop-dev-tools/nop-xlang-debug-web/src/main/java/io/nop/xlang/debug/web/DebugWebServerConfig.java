/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.xlang.debugger.session.SessionDebugManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_REGISTER_XLANG;
import static io.nop.xlang.debugger.session.XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_ENABLED;

/**
 * 调试 WS 网关初始化（plan 2471 Phase 2）：web 开关门（{@code nop.xlang.debug-web.enabled}，
 * 蕴含 debugger.enabled 的 fail-fast 已由 XLangDebuggerInitializer 承担）；装配
 * {@link DebugGatewayRuntime}（鉴权器复用 BeanContainer 的 IUserContextExtractor——
 * 生产实现 ILoginService；无 bean 时装配拒绝型 extractor，保证"无鉴权不开放"红线），
 * 注册 SessionListener（evt.* 出站接线），静态单例供端点适配器取用。
 *
 * <p>order 必须晚于 XLangDebuggerInitializer（REGISTER_XLANG 优先级）——web 开启时
 * 会话管理器在该 initializer 内完成 init。destroy 顺序：先网关清扫（serverShutdown
 * 语义逐会话推 evt.sessionClosed）再停定时器；SessionDebugManager.shutdownAll 幂等，
 * 与 XLangDebuggerInitializer.destroy 的重复清扫无冲突。
 */
public class DebugWebServerConfig implements ICoreInitializer {
    static final Logger LOG = LoggerFactory.getLogger(DebugWebServerConfig.class);

    private DebugGatewayRuntime runtime;

    @Override
    public boolean isEnabled() {
        return CFG_XLANG_DEBUG_WEB_ENABLED.get();
    }

    @Override
    public int order() {
        return INITIALIZER_PRIORITY_REGISTER_XLANG + 1;
    }

    @Override
    public void initialize() {
        SessionDebugManager sessionManager = SessionDebugManager.instance();
        IUserContextExtractor extractor = null;
        if (BeanContainer.isInitialized())
            extractor = BeanContainer.instance().tryGetBeanByType(IUserContextExtractor.class);
        if (extractor == null) {
            // 无鉴权实现可用：装配拒绝型 extractor（extractFromHeaders 返回 null → 4401），
            // 调试通道不因缺 bean 而开放（D0 红线：禁止无鉴权调试通道）
            extractor = headers -> null;
            LOG.warn("nop.xlang.debug-web.no-user-context-extractor");
        }
        runtime = new DebugGatewayRuntime(sessionManager, extractor);
        runtime.getBinder().install();
        // REPL 服务装配（plan 2473）：checker 缺失时传 null——guard 对缺失 checker 拒绝（403），
        // "无鉴权不开放"红线
        runtime.setReplService(new ReplService(runtime,
                new ReplAccessGuard(extractor,
                        BeanContainer.isInitialized()
                                ? BeanContainer.instance().tryGetBeanByType(IActionAuthChecker.class)
                                : null)));
        DebugGatewayRuntime.installSingleton(runtime);
        LOG.info("nop.xlang.debug-web.initialized:leaseTtl={},renewInterval={},grace={}",
                runtime.leaseTtlMillis(), runtime.renewIntervalMillis(), runtime.graceMillis());
    }

    @Override
    public void destroy() {
        DebugGatewayRuntime installed = runtime;
        runtime = null;
        DebugGatewayRuntime.installSingleton(null);
        if (installed != null) {
            try {
                installed.getBinder().shutdown();
            } finally {
                installed.shutdownTimer();
            }
        }
    }
}
