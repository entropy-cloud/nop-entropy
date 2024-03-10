/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface XLangConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(XLangConfigs.class);

    @Description("解析xplExpr时打印调试信息")
    IConfigReference<Boolean> CFG_EXPR_PRINT_DEBUG_INFO_WHEN_PARSE_XPL_EXPR = varRef(
            s_loc,"nop.xlang.expr.print-debug-info-when-parse-xpl-expr", Boolean.class, true);

    @Description("XLang语法中括号嵌套的最大层数")
    IConfigReference<Integer> CFG_XLANG_ANTLR_MAX_NESTED_LEVEL = varRef(s_loc,"nop.xlang.antlr.max-nested-level",
            Integer.class, 100);

    @Description("标签库加载到内存中之后，是否监控需要重新加载")
    IConfigReference<Boolean> CFG_XPL_LIB_TAG_RELOADABLE = varRef(s_loc,"nop.xlang.xpl.lib-tag-reloadable", Boolean.class,
            true);

    @Description("XLang调试器端口")
    IConfigReference<Integer> CFG_XLANG_DEBUGGER_PORT = varRef(s_loc,"nop.xlang.debugger.port", Integer.class, 12345);

    @Description("XLang调试器端口")
    IConfigReference<Integer> CFG_XLANG_DEBUGGER_MAX_DATA_LEN = varRef(s_loc,"nop.xlang.debugger.max-data-len", Integer.class,
            1024 * 1024 * 2);

    @Description("是否启用XLang调试服务。如果启用，则所有的表达式会在DebugExpressionExecutor中执行")
    IConfigReference<Boolean> CFG_XLANG_DEBUGGER_ENABLED = varRef(s_loc,"nop.xlang.debugger.enabled", Boolean.class, false);

    @Description("如果大于0，则表示XLang调试服务启动后在一段时间内阻塞当前程序执行，等待外部调试器连接。单位为秒")
    IConfigReference<Integer> CFG_XLANG_DEBUGGER_WAIT_CONNECTION_SECONDS = varRef(s_loc,
            "nop.xlang.debugger.wait-connection-seconds", Integer.class, 1);
}
