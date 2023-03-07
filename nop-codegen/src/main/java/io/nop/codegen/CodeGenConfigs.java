/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;

import static io.nop.api.core.config.AppConfig.varRef;

public interface CodeGenConfigs {
    @Description("是否跟踪系统内的Java反射调用，自动生成Graalvm原生镜像所需的的配置文件")
    IConfigReference<Boolean> CFG_CODEGEN_TRACE_ENABLED = varRef("nop.codegen.trace.enabled", Boolean.class, false);

    IConfigReference<String> CFG_CODEGEN_TRACE_DIR = varRef("nop.codegen.trace-dir", String.class, null);
}
