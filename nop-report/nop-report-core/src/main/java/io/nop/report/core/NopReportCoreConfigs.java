/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface NopReportCoreConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopReportCoreConfigs.class);

    @Description("单个 sheet 允许展开生成的最大行数（fail-fast 防 OOM；0 表示不限制）。模板级 expandMaxCount 语义不受影响")
    IConfigReference<Integer> CFG_MAX_EXPAND_ROWS = varRef(s_loc, "nop.report.max-expand-rows",
            Integer.class, 100_000);
}
