/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.messages.SiteMapBean;
import io.nop.auth.core.sitemap.ISiteMapProvider;
import io.nop.auth.service.NopAuthConstants;
import io.nop.commons.util.StringHelper;
import jakarta.inject.Inject;

import java.util.Set;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SITE_MAP_SUPPORT_DEBUG;
import static io.nop.auth.service.NopAuthErrors.ARG_SITE_ID;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_UNKNOWN_SITE;

@BizModel("SiteMapApi")
public class SiteMapApiBizModel {

    @Inject
    protected ISiteMapProvider siteMapProvider;

    @BizQuery
    @Auth(publicAccess = true)
    public SiteMapBean getSiteMap(@Optional @Name("siteId") String siteId,
                                  @Optional @Name("includeFunctionPoints") boolean includeFunctionPoints) {
        if (StringHelper.isEmpty(siteId))
            siteId = NopAuthConstants.SITE_ID_MAIN;

        String locale = ContextProvider.currentLocale();

        SiteMapBean siteMap = siteMapProvider.getSiteMap(siteId, locale);
        if (siteMap == null)
            throw new NopException(ERR_AUTH_UNKNOWN_SITE).param(ARG_SITE_ID, siteId);

        siteMap = filterForUser(siteMap, includeFunctionPoints);

        siteMap.setSupportDebug(CFG_AUTH_SITE_MAP_SUPPORT_DEBUG.get());

        return siteMap;
    }

    // 只保留当前用户可以访问的菜单
    protected SiteMapBean filterForUser(SiteMapBean site, boolean includeFunctionPoints) {
        IUserContext user = IUserContext.get();
        String userId = user == null ? null : user.getUserId();
        String deptId = user == null ? null : user.getDeptId();
        Set<String> roles = user == null ? null : user.getRoles();

        site = siteMapProvider.filterAllowedMenu(site, userId,
                deptId, roles, includeFunctionPoints);
        return site;
    }
}
