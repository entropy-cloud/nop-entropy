/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.auth;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.sitemap.SiteMapProviderImpl;
import jakarta.inject.Inject;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SKIP_CHECK_FOR_ADMIN;

/**
 * 操作权限检查器。
 * <p>
 * {@code nop.auth.skip-check-for-admin} 的默认值由 {@link NopAuthConfigs#CFG_AUTH_SKIP_CHECK_FOR_ADMIN}
 * 单一来源决定（默认 false）。此处不再使用 @InjectValue 的内联 fallback，避免出现两套默认值互相冲突
 * （H-2：此前 @InjectValue fallback 为 true，而 IConfigReference 默认为 false，导致管理员默认绕过权限检查）。
 */
public class DefaultActionAuthChecker implements IActionAuthChecker {
    private SiteMapProviderImpl siteMapProvider;

    @Inject
    public void setSiteMapProvider(SiteMapProviderImpl provider) {
        siteMapProvider = provider;
    }

    @Override
    public boolean isPermitted(String permission, ISecurityContext context) {
        if (CFG_AUTH_SKIP_CHECK_FOR_ADMIN.get()) {
            IUserContext userContext = context.getUserContext();
            if (userContext.isUserInRole(NopAuthConstants.ROLE_ADMIN) || userContext.isUserInRole(NopAuthConstants.ROLE_NOP_ADMIN))
                return true;
        }
        return siteMapProvider.isPermitted(permission, context);
    }
}
