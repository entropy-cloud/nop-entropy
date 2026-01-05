/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface CodeGenConfigs {
    SourceLocation S_LOC = SourceLocation.fromClass(CodeGenConfigs.class);
    @Description("是否跟踪系统内的Java反射调用，自动生成Graalvm原生镜像所需的的配置文件")
    IConfigReference<Boolean> CFG_CODEGEN_TRACE_ENABLED = varRef(S_LOC, "nop.codegen.trace.enabled", Boolean.class, false);

    IConfigReference<String> CFG_CODEGEN_TRACE_DIR = varRef(S_LOC, "nop.codegen.trace-dir", String.class, null);
}
