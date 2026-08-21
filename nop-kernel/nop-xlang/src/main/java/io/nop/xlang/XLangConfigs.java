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

    @Description("XLang调试服务传输的最大数据长度")
    IConfigReference<Integer> CFG_XLANG_DEBUGGER_MAX_DATA_LEN = varRef(s_loc,"nop.xlang.debugger.max-data-len", Integer.class,
            1024 * 1024 * 2);

    @Description("是否启用XLang调试服务。如果启用，则所有的表达式会在DebugExpressionExecutor中执行")
    IConfigReference<Boolean> CFG_XLANG_DEBUGGER_ENABLED = varRef(s_loc,"nop.xlang.debugger.enabled", Boolean.class, false);

    @Description("是否启用类型推导功能。启用后会在编译时进行静态类型检查和推导")
    IConfigReference<Boolean> CFG_XLANG_TYPE_INFERENCE_ENABLED = varRef(s_loc,"nop.xlang.type-inference.enabled", Boolean.class, false);

    @Description("如果大于0，则表示XLang调试服务启动后在一段时间内阻塞当前程序执行，等待外部调试器连接。单位为秒")
    IConfigReference<Integer> CFG_XLANG_DEBUGGER_WAIT_CONNECTION_SECONDS = varRef(s_loc,
            "nop.xlang.debugger.wait-connection-seconds", Integer.class, 1);

    @Description("是否启用java执行后端（静态生成物路径）。仅在后端模块已注册时生效；未注册=classpath缺席的安静缺省")
    IConfigReference<Boolean> CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED = varRef(s_loc,
            "nop.xlang.execution.java-backend-enabled", Boolean.class, true);

    @Description("是否启用truffle执行后端（动态翻译路径）。仅在后端模块已注册时生效；native部署形态下结构性不适用")
    IConfigReference<Boolean> CFG_XLANG_EXECUTION_TRUFFLE_BACKEND_ENABLED = varRef(s_loc,
            "nop.xlang.execution.truffle-backend-enabled", Boolean.class, true);

    @Description("强制解释器诊断模式：全部路由短路到解释器执行，且不记降级观测事件（隔离后端问题的诊断开关）")
    IConfigReference<Boolean> CFG_XLANG_EXECUTION_FORCE_INTERPRETER = varRef(s_loc,
            "nop.xlang.execution.force-interpreter", Boolean.class, false);

    @Description("部署形态标记：auto（缺省，探测系统属性org.graalvm.nativeimage.kind）| jvm | native-image。native-image下truffle后端结构性不启用")
    IConfigReference<String> CFG_XLANG_EXECUTION_DEPLOYMENT_FORM = varRef(s_loc,
            "nop.xlang.execution.deployment-form", String.class, "auto");

    @Description("声明本部署应存在java后端生成产物（构建任务已接入）。true且classpath无生成类清单文件时=构建管线漏跑缺陷：java后端注册不可用条目+全局WARN。缺省false=未接入构建任务的合法空态（静默）")
    IConfigReference<Boolean> CFG_XLANG_EXECUTION_JAVA_BACKEND_REQUIRE_MANIFEST = varRef(s_loc,
            "nop.xlang.execution.java-backend.require-manifest", Boolean.class, false);
}
