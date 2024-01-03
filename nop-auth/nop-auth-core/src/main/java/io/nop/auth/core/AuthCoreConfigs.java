/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface AuthCoreConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(AuthCoreConfigs.class);

    @Description("是否启用前端调试模式")
    IConfigReference<Boolean> CFG_AUTH_SITE_MAP_SUPPORT_DEBUG = varRef(s_loc, "nop.auth.site-map.support-debug", Boolean.class,
            false);

    @Description("是否设置cookie的secure属性为true")
    IConfigReference<Boolean> CFG_AUTH_USE_SECURE_COOKIE =
            varRef(s_loc, "nop.auth.use-secure-cookie", Boolean.class, false);
}
