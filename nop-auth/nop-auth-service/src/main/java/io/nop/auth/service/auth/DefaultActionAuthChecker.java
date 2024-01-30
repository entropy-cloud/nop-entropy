/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.auth;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.sitemap.SiteMapProviderImpl;
import jakarta.inject.Inject;

public class DefaultActionAuthChecker implements IActionAuthChecker {
    private SiteMapProviderImpl siteMapProvider;

    private boolean skipCheckForAdmin;

    @InjectValue("@cfg:nop.auth.skip-check-for-admin|true")
    public void setSkipCheckForAdmin(boolean skipCheckForAdmin) {
        this.skipCheckForAdmin = skipCheckForAdmin;
    }

    @Inject
    public void setSiteMapProvider(SiteMapProviderImpl provider) {
        siteMapProvider = provider;
    }

    @Override
    public boolean isPermitted(String permission, ISecurityContext context) {
        if (skipCheckForAdmin && context.getUserContext().isUserInRole(NopAuthConstants.ROLE_ADMIN))
            return true;
        return siteMapProvider.isPermitted(permission, context);
    }
}