/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface NopReportConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopReportConfigs.class);

    @Description("报表授权检查的豁免角色列表，逗号分隔。默认为空，即无隐式豁免角色")
    IConfigReference<String> CFG_REPORT_AUTH_EXEMPT_ROLES = varRef(s_loc, "nop.report.auth.exempt-roles",
            String.class, "");
}
