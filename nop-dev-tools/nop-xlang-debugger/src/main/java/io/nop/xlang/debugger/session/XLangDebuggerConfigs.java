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

    @Description("调试日志攒批条数阈值。会话日志管道缓冲达到该条数立即flush（evt.logs出站）")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_LOG_BATCH_SIZE = varRef(s_loc,
            "nop.xlang.debug-web.log.batch-size", Integer.class, 64);

    @Description("调试日志攒批flush间隔（毫秒）。未达条数阈值的缓冲日志按该间隔定时flush；0=禁用定时flush（仅条数阈值与销毁flush）")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_LOG_FLUSH_INTERVAL = varRef(s_loc,
            "nop.xlang.debug-web.log.flush-interval-ms", Integer.class, 300);

    @Description("REPL执行超时（毫秒）。请求timeoutMs与其取小；上限120000；绝对时钟，断点挂起时间计入（D6 §3.4 一期限制）")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_REPL_EXECUTE_TIMEOUT = varRef(s_loc,
            "nop.xlang.debug-web.repl.execute-timeout-ms", Integer.class, 30000);

    @Description("REPL编译超时（毫秒）。编译期常量/宏求值会执行代码，超时非纯防御值（D6 §3.4）")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_REPL_COMPILE_TIMEOUT = varRef(s_loc,
            "nop.xlang.debug-web.repl.compile-timeout-ms", Integer.class, 10000);

    @Description("REPL source最大字节数。超限按413语义拒绝（D6 §3.4 限制表）")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_REPL_MAX_SOURCE_BYTES = varRef(s_loc,
            "nop.xlang.debug-web.repl.max-source-bytes", Integer.class, 262144);

    @Description("REPL全局并发执行数上限。超限按限流错误响应（不关闭通道）")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_REPL_MAX_CONCURRENT = varRef(s_loc,
            "nop.xlang.debug-web.repl.max-concurrent", Integer.class, 4);

    @Description("REPL允许import的白名单（逗号分隔的Java类FQN/xlib命名空间）。空=拒绝一切import（D6 §3.3 默认拒绝）")
    IConfigReference<String> CFG_XLANG_DEBUG_WEB_REPL_IMPORT_WHITELIST = varRef(s_loc,
            "nop.xlang.debug-web.repl.import-whitelist", String.class, "");

    @Description("watch 求值（debugger.getExprValue/expandExprValue）deadline 窗口时长（plan 2478 SEC-07）。超时经取消通道打断并返回错误响应")
    IConfigReference<Integer> CFG_XLANG_DEBUG_WEB_WATCH_EVAL_TIMEOUT = varRef(s_loc,
            "nop.xlang.debug-web.watch.eval-timeout-ms", Integer.class, 5000);
}
