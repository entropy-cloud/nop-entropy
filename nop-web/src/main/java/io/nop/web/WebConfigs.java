/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface WebConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(WebConfigs.class);
    @Description("启动时自动加载xjs和xcss文件")
    IConfigReference<Boolean> CFG_WEB_AUTO_LOAD_DYNAMIC_FILE = varRef(s_loc,
            "nop.web.auto-load-dynamic-file", Boolean.class, false);

    @Description("启用XCSS文件")
    IConfigReference<Boolean> CFG_WEB_USE_DYNAMIC_CSS = varRef(s_loc,
            "nop.web.use-dynamic-css", Boolean.class, true);

    @Description("启用XJS文件")
    IConfigReference<Boolean> CFG_WEB_USE_DYNAMIC_JS = varRef(s_loc,
            "nop.web.use-dynamic-js", Boolean.class, true);

    @Description("并行验证页面模型的线程数")
    IConfigReference<Integer> CFG_WEB_PAGE_VALIDATION_THREAD_COUNT = varRef(s_loc,
            "nop.web.page-validation-thread-count", Integer.class, 1);

}
