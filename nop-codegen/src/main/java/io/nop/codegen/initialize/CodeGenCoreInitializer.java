/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen.initialize;

import io.nop.codegen.graalvm.GraalvmConfigGenerator;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.reflect.ReflectionManager;

import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_ENABLED;
import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_INTERNAL;

public class CodeGenCoreInitializer implements ICoreInitializer {

    @Override
    public int order() {
        return INITIALIZER_PRIORITY_INTERNAL;
    }

    @Override
    public boolean isEnabled() {
        return CFG_CODEGEN_TRACE_ENABLED.get();
    }

    @Override
    public void initialize() {
        ReflectionManager.instance().setRecordForNativeImage(true);
    }

    @Override
    public void destroy() {
        GraalvmConfigGenerator.instance().generateGraalvmConfig();
        ReflectionManager.instance().setRecordForNativeImage(false);
    }
}
