/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.sitemap;

import io.nop.api.core.auth.IRolePermissionMapping;
import io.nop.auth.api.messages.SiteMapBean;

import java.util.List;
import java.util.Set;

public interface ISiteMapProvider extends IRolePermissionMapping {

    void refreshCache();

    /**
     * 返回用户可以访问的界面菜单项
     *
     * @param siteId 站点id。对于较大的系统可以分为多个子站点。
     * @param locale 菜单项的显示语言
     * @return 菜单树
     */
    SiteMapBean getSiteMap(String siteId, String locale);

    /**
     * 返回用户可以访问的界面菜单项的id
     *
     * @param siteId 站点id
     * @return siteEntry的id列表
     */
    Set<String> getAllowedSiteEntries(String siteId, String userId, String deptId, Set<String> roleIds);

    SiteMapBean filterAllowedMenu(SiteMapBean site, String userId, String deptId,
                                  Set<String> roleIds, boolean includeFunctionPoints);

    List<SiteMapBean> loadStaticSiteMap();
}