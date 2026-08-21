/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.initialize;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.xlang.backend.EvalBackendRegistry;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import io.nop.xlang.java.gen.GeneratedManifestFiles;

/**
 * java 执行后端显式注册（模块初始化时机，{@code XLangCoreInitializer} +
 * {@code JaninoScriptCompiler.register()} 先例同款：ICoreInitializer + META-INF/services，
 * 无注解扫描）。注册为静态生成物能力条目；初始化失败语义 = 不可用条目（保留原因）不阻断启动。
 *
 * <p>运行时供给闭环（I11）：注册后装载 classpath 双清单文件产物（多 jar 聚合内建）填充 I10
 * 供给缝——清单在场即激活（模块接入构建任务即生效）；缺省（classpath 无清单）为空态，双缝不设值，
 * 行为与现状一致。漏跑判别子：{@code nop.xlang.execution.java-backend.require-manifest=true}
 * 且清单缺席 = 构建管线漏跑缺陷（不可用条目 + 全局 WARN，见 {@link GeneratedManifestFiles}）。
 */
public class XLangJavaBackendInitializer implements ICoreInitializer {

    @Override
    public int order() {
        // 在 XLang 核心注册之后注册后端条目
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_XLANG + 10;
    }

    @Override
    public void initialize() {
        EvalBackendRegistry.instance().register(JavaEvalExecutionBackend.instance());
        GeneratedManifestFiles.installSupplies(getClass().getClassLoader());
    }

    @Override
    public void destroy() {
        GeneratedManifestFiles.clearSupplies();
        EvalBackendRegistry.instance().unregister(JavaEvalExecutionBackend.instance());
    }
}
