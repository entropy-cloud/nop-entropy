/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

    @Description("是否设置cookie的secure属性为true。默认true；本地HTTP开发可通过配置设为false关闭（开发逃生通道）")
    IConfigReference<Boolean> CFG_AUTH_USE_SECURE_COOKIE =
            varRef(s_loc, "nop.auth.use-secure-cookie", Boolean.class, true);

    @Description("是否信任来自可信反向代理的 X-Forwarded-Tenant 头。" +
            "默认false：servicePublic 路径不信任任何客户端提供的租户头，避免租户注入")
    IConfigReference<Boolean> CFG_AUTH_TRUST_FORWARDED_TENANT =
            varRef(s_loc, "nop.auth.trust-forwarded-tenant", Boolean.class, false);


    @Description("是否使用用户ID作为创建人、修改人等审计字段，缺省为false，使用userName")
    IConfigReference<Boolean> CFG_AUTH_USE_USER_ID_FOR_AUDIT_FIELDS =
            varRef(s_loc, "nop.auth.use-user-id-for-audit-fields", Boolean.class, false);
}
