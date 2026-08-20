/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.truffle.initialize;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.xlang.backend.EvalBackendRegistry;
import io.nop.xlang.truffle.backend.TruffleEvalExecutionBackend;

/**
 * truffle 执行后端显式注册（模块初始化时机，{@code XLangCoreInitializer} +
 * {@code JaninoScriptCompiler.register()} 先例同款：ICoreInitializer + META-INF/services，
 * 无注解扫描）。注册时探测共享 Engine 可用性，失败 → 不可用条目（保留原因）不阻断启动。
 */
public class XLangTruffleBackendInitializer implements ICoreInitializer {

    @Override
    public int order() {
        // 在 XLang 核心注册之后注册后端条目
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_XLANG + 20;
    }

    @Override
    public void initialize() {
        TruffleEvalExecutionBackend backend = TruffleEvalExecutionBackend.instance();
        backend.probeInitialization();
        EvalBackendRegistry.instance().register(backend);
    }

    @Override
    public void destroy() {
        TruffleEvalExecutionBackend backend = TruffleEvalExecutionBackend.instance();
        EvalBackendRegistry.instance().unregister(backend);
        backend.close();
    }
}
