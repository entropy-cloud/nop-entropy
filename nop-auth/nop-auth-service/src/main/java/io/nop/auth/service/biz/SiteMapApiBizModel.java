/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.messages.SiteMapBean;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.sitemap.ISiteMapProvider;

import io.nop.auth.service.NopAuthConstants;
import io.nop.commons.util.StringHelper;
import jakarta.inject.Inject;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SITE_MAP_SUPPORT_DEBUG;
import static io.nop.auth.service.NopAuthErrors.ARG_SITE_ID;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_UNKNOWN_SITE;

@BizModel("SiteMapApi")
public class SiteMapApiBizModel {

    @Inject
    protected ISiteMapProvider siteMapProvider;

    @BizQuery
    public SiteMapBean getSiteMap(@Optional @Name("siteId") String siteId) {
        if(StringHelper.isEmpty(siteId))
            siteId = NopAuthConstants.SITE_ID_MAIN;

        String locale = ContextProvider.currentLocale();

        SiteMapBean siteMap = siteMapProvider.getSiteMap(siteId, locale);
        if (siteMap == null)
            throw new NopException(ERR_AUTH_UNKNOWN_SITE).param(ARG_SITE_ID, siteId);

        siteMap = filterForUser(siteMap);

        siteMap.setSupportDebug(CFG_AUTH_SITE_MAP_SUPPORT_DEBUG.get());

        return siteMap;
    }

    // 只保留当前用户可以访问的菜单
    protected SiteMapBean filterForUser(SiteMapBean site) {
        IUserContext user = IUserContext.get();
        if (user != null) {
            site = siteMapProvider.filterAllowedMenu(site, user.getUserId(), user.getDeptId(), user.getRoles());
        }
        return site;
    }
}
