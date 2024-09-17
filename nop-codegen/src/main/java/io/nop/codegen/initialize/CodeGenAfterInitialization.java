/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.initialize;

import io.nop.codegen.graalvm.GraalvmConfigGenerator;
import io.nop.codegen.graalvm.ProxyConfigGenerator;
import io.nop.codegen.graalvm.ReflectConfigGenerator;
import io.nop.core.initialize.ICoreInitializer;

import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_ENABLED;
import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_POST_PROCESS;

public class CodeGenAfterInitialization implements ICoreInitializer {

    @Override
    public int order() {
        return INITIALIZER_PRIORITY_POST_PROCESS;
    }

    @Override
    public boolean isEnabled() {
        return CFG_CODEGEN_TRACE_ENABLED.get();
    }

    @Override
    public void initialize() {
        GraalvmConfigGenerator.instance().generateVfsIndex();
        GraalvmConfigGenerator.instance().generateGraalvmConfig();
    }

    @Override
    public void destroy() {

    }
}
