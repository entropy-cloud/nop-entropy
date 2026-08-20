/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.initialize;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.xlang.backend.EvalBackendRegistry;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;

/**
 * java 执行后端显式注册（模块初始化时机，{@code XLangCoreInitializer} +
 * {@code JaninoScriptCompiler.register()} 先例同款：ICoreInitializer + META-INF/services，
 * 无注解扫描）。注册为静态生成物能力条目；初始化失败语义 = 不可用条目（保留原因）不阻断启动。
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
    }

    @Override
    public void destroy() {
        EvalBackendRegistry.instance().unregister(JavaEvalExecutionBackend.instance());
    }
}
