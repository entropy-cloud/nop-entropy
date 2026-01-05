/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface NopDynConfigs {
    SourceLocation S_LOC = SourceLocation.fromClass(NopDynConfigs.class);

    @Description("启动时是否自动初始化动态模块")
    IConfigReference<Boolean> CFG_DYN_GEN_CODE_WHEN_INIT =
            varRef(S_LOC, "nop.dyn.gen-code-when-init", Boolean.class, true);

    @Description("最多允许多少动态对象")
    IConfigReference<Integer> CFG_DYN_MAX_BIZ_OBJECTS =
            varRef(S_LOC, "nop.dyn.max-biz-objects", Integer.class, 1000);
}
