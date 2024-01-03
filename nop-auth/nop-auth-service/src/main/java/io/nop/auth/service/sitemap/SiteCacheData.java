/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.sitemap;

import io.nop.api.core.auth.ISecurityContext;
import io.nop.auth.api.messages.SiteMapBean;
import io.nop.commons.util.CollectionHelper;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SiteCacheData {
    // 从siteId得到SiteMapBean
    private Map<String, SiteMapBean> siteMaps;

    // 从权限标识映射到所有具有该权限标识的角色id
    private Map<String, Set<String>> permissionToRoles;

    private Map<String, Set<String>> resourceToRoles;

    public Map<String, SiteMapBean> getSiteMaps() {
        return siteMaps;
    }

    public void setSiteMaps(Map<String, SiteMapBean> siteMaps) {
        this.siteMaps = siteMaps;
    }

    public Map<String, Set<String>> getPermissionToRoles() {
        return permissionToRoles;
    }

    public void setPermissionToRoles(Map<String, Set<String>> permissionToRoles) {
        this.permissionToRoles = permissionToRoles;
    }

    public SiteMapBean getSite(String siteId) {
        return siteMaps.get(siteId);
    }

    public Map<String, Set<String>> getResourceToRoles() {
        return resourceToRoles;
    }

    public void setResourceToRoles(Map<String, Set<String>> resourceToRoles) {
        this.resourceToRoles = resourceToRoles;
    }

    /**
     * 要求满足所有permission，所以取每个permission对应的roles集合的交集
     */
    public Set<String> getRolesWithPermission(Set<String> permissions) {
        Set<String> ret = new HashSet<>();
        int index = 0;
        for (String permission : permissions) {
            Set<String> roles = permissionToRoles.get(permission);
            if (CollectionHelper.isEmpty(roles))
                return Collections.emptySet();

            if (index == 0) {
                ret.addAll(roles);
                index ++;
            } else {
                ret.retainAll(roles);
                if (ret.isEmpty())
                    return ret;
            }
        }
        return ret;
    }

    public boolean isPermitted(String permission, ISecurityContext context) {
        Set<String> roles = permissionToRoles.get(permission);
        if (CollectionHelper.isEmpty(roles))
            return false;

        return context.getUserContext().isUserInAnyRole(roles);
    }
}