/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * 调试器 web 通道的模块内配置常量。键名归属 D0 §3.3（nop.xlang.debug-web.*），
 * 注册提前至 item 8（initialize 分支需要读取）；刻意不放 nop-xlang 的 XLangConfigs，
 * 保持方案 A 的零保护区声明。
 */
@Locale("zh-CN")
public interface XLangDebuggerConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(XLangDebuggerConfigs.class);

    @Description("是否启用XLang调试Web通道。仅在nop.xlang.debugger.enabled=true时可用；启用后全局executor切换为会话路由形态")
    IConfigReference<Boolean> CFG_XLANG_DEBUG_WEB_ENABLED = varRef(s_loc, "nop.xlang.debug-web.enabled", Boolean.class,
            false);

    @Description("调试会话租约TTL（毫秒）。挂起线程超过TTL未续期将自动放行（resume语义）")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_LEASE_TTL = varRef(s_loc, "nop.xlang.debug-web.lease.ttl-ms",
            Integer.class, 30000);

    @Description("租约续期间隔（毫秒）。网关按此限流lease.renew请求")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_LEASE_RENEW_INTERVAL = varRef(s_loc,
            "nop.xlang.debug-web.lease.renew-interval-ms", Integer.class, 10000);

    @Description("断连宽限期（毫秒）。连接断开且宽限期内未rebind则销毁会话")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_LEASE_GRACE = varRef(s_loc,
            "nop.xlang.debug-web.lease.grace-ms", Integer.class, 15000);
}
