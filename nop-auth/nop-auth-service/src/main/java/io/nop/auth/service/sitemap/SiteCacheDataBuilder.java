/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.sitemap;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.auth.api.messages.SiteMapBean;
import io.nop.auth.api.messages.SiteResourceBean;
import io.nop.auth.dao.entity.NopAuthResource;
import io.nop.auth.dao.entity.NopAuthRoleResource;
import io.nop.auth.dao.entity.NopAuthSite;
import io.nop.auth.service.NopAuthConstants;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static io.nop.auth.service.NopAuthConstants.RESOURCE_TYPE_TOP_MENU;
import static io.nop.commons.util.StringHelper.isYes;

public class SiteCacheDataBuilder {
    static final Logger LOG = LoggerFactory.getLogger(SiteCacheDataBuilder.class);

    private Map<String, SiteMapBean> allSiteMap = new HashMap<>();

    private Map<String, Set<String>> permissionToResources = new HashMap<>();

    // resourceId to roleIds
    private Map<String, Set<String>> resourceToRoles = new HashMap<>();

    private Map<String, SiteResourceBean> entryMap = new HashMap<>();

    private Map<String, Set<String>> parentToChildren = new HashMap<>();

    private Map<String, String> childToParent = new HashMap<>();

    private Map<String, Set<String>> rootMenus = new HashMap<>();

    public SiteCacheDataBuilder addStaticConfigs(Collection<SiteMapBean> sites) {
        if (sites != null) {
            for (SiteMapBean site : sites) {
                addStaticConfig(site);
            }
        }
        return this;
    }

    /**
     * 合并静态的站点资源定义
     */
    public SiteCacheDataBuilder addStaticConfig(SiteMapBean site) {
        SiteMapBean siteMap = makeSiteMap(site.getId());
        siteMap.setDisplayName(site.getDisplayName());
        siteMap.setConfigVersion(site.getConfigVersion());

        List<SiteResourceBean> resources = site.getResources();
        if (resources != null) {
            for (SiteResourceBean resource : resources) {
                rootMenus.computeIfAbsent(site.getId(), k -> new LinkedHashSet<>()).add(resource.getId());

                mergeResource(resource);
            }
        }

        return this;
    }

    void mergeResource(SiteResourceBean resource) {
        SiteResourceBean copy = resource.cloneInstance();
        // 后面buildEntryTree的时候会重新建立children。buildEntryTree会检查是否有循环引用
        copy.setChildren(null);

        entryMap.putIfAbsent(resource.getId(), copy);

        List<SiteResourceBean> children = resource.getChildren();
        if (children != null) {
            for (SiteResourceBean child : children) {
                child.setParentId(resource.getId());

                // 考虑到动态配置的资源可能会覆盖静态配置的资源，所以这里不构建父子关系，而是延迟到合并完成之后进行
                //addParentChild(resource.getId(), child.getId());
                mergeResource(child);
            }
        }
    }

    void addParentChild(String parentId, String childId) {
        Set<String> children = parentToChildren.computeIfAbsent(parentId, k -> new LinkedHashSet<>());
        children.add(childId);

        String oldParent = childToParent.get(childId);
        if (oldParent != null && !oldParent.equals(parentId)) {
            parentToChildren.computeIfAbsent(oldParent, k -> new LinkedHashSet<>()).remove(childId);
        }
        childToParent.put(childId, parentId);
    }

    /**
     * 如果同时存在静态配置和动态配置，则会替换对应的静态配置属性
     */
    public SiteCacheDataBuilder addDynamicConfig(List<NopAuthSite> sites, List<NopAuthResource> resources,
                                                 List<NopAuthRoleResource> roleResources) {
        for (NopAuthSite site : sites) {
            SiteMapBean siteMap = makeSiteMap(site.getSiteId());
            siteMap.setDisplayName(site.getDisplayName());
            siteMap.setConfigVersion(site.getConfigVersion());
        }

        for (NopAuthRoleResource roleResource : roleResources) {
            resourceToRoles.computeIfAbsent(roleResource.getResourceId(), k -> new TreeSet<>())
                    .add(roleResource.getRoleId());
        }

        for (NopAuthResource resource : resources) {
            SiteResourceBean entry = newSiteResource(resource);
//            if (resource.getParentId() != null) {
//                addParentChild(resource.getParentId(), entry.getId());
//            }

            if (RESOURCE_TYPE_TOP_MENU.equals(resource.getResourceType())) {
                rootMenus.computeIfAbsent(resource.getSiteId(), k -> new LinkedHashSet<>()).add(entry.getId());
            }
        }

        return this;
    }

    public SiteCacheData build() {
        initParentChildren();
        buildEntryTree(new HashSet<>());

        for (SiteResourceBean resource : entryMap.values()) {
            if (resource.isNoAuth())
                continue;

            if (resource.getRoles() != null && !resource.getRoles().isEmpty()) {
                resourceToRoles.computeIfAbsent(resource.getId(), k -> new HashSet<>()).addAll(resource.getRoles());
            }

            Set<String> permissions = resource.getPermissions();
            if (permissions != null) {
                for (String permission : permissions) {
                    permissionToResources.computeIfAbsent(permission, k -> new HashSet<>()).add(resource.getId());
                }
            }
        }

        for (String resourceId : new HashSet<>(resourceToRoles.keySet())) {
            Set<String> roles = resourceToRoles.get(resourceId);
            SiteResourceBean resource = entryMap.get(resourceId);
            cascadeResourceToRoles(resource, roles);
        }

        Map<String, Set<String>> permissionToRoles = new HashMap<>();
        permissionToResources.forEach((permission, resources) -> {
            Set<String> roles = permissionToRoles.computeIfAbsent(permission, k -> new HashSet<>());
            resources.forEach(resId -> {
                Set<String> resourceRoles = resourceToRoles.get(resId);
                if (resourceRoles != null)
                    roles.addAll(resourceRoles);
            });
        });

        removeInactive();
        fixResource();

        SiteCacheData data = new SiteCacheData();
        data.setSiteMaps(allSiteMap);
        data.setResourceToRoles(resourceToRoles);
        data.setPermissionToRoles(permissionToRoles);

        return data;
    }

    void fixResource() {
        for (SiteResourceBean resource : entryMap.values()) {
            if (!NopAuthConstants.RESOURCE_TYPE_FUNCTION_POINT.equals(resource.getResourceType())) {
                // 如果是菜单项，则routePath不应该为空
                if (StringHelper.isEmpty(resource.getRoutePath())) {
                    resource.setRoutePath(StringHelper.appendPath("/", resource.getId()));
                }
            }
            if (StringHelper.isEmpty(resource.getUrl()) && StringHelper.isEmpty(resource.getComponent())) {
                resource.setComponent("layouts/default/index");
            }
        }
    }

//    boolean isPageUrl(String url) {
//        if (StringHelper.isEmpty(url))
//            return false;
//        return url.indexOf(':') < 0 && url.indexOf('?') < 0 && url.startsWith("/") && url.endsWith(".page.yaml");
//    }

    /**
     * 删除所有不处于活动状态的节点
     */
    void removeInactive() {
        for (SiteMapBean siteMap : allSiteMap.values()) {
            siteMap.removeInactive();
            siteMap.sortResources();
        }
    }

    void cascadeResourceToRoles(SiteResourceBean resource, Set<String> roles) {
        if (resource.isAuthCascadeUp()) {
            String parentId = this.childToParent.get(resource.getId());
            if (parentId != null) {
                SiteResourceBean parent = this.entryMap.get(parentId);
                if (parent != null) {
                    resourceToRoles.computeIfAbsent(parent.getId(), k -> new HashSet<>()).addAll(roles);
                    cascadeResourceToRoles(parent, roles);
                }
            }
        }
    }

    SiteResourceBean newSiteResource(NopAuthResource resource) {
        SiteResourceBean entry = entryMap.computeIfAbsent(resource.getResourceId(), k -> new SiteResourceBean());
        entry.setId(resource.getResourceId());
        entry.setResourceType(resource.getResourceType());
        entry.setParentId(resource.getParentId());
        if (!StringHelper.isEmpty(resource.getRoutePath()))
            entry.setRoutePath(resource.getRoutePath());
        if (entry.getRoutePath() == null)
            entry.setRoutePath(StringHelper.appendPath("/", entry.getId()));
        if (!StringHelper.isEmpty(resource.getIcon()))
            entry.setIcon(resource.getIcon());
        if (!StringHelper.isEmpty(resource.getDisplayName()))
            entry.setDisplayName(resource.getDisplayName());
        if (!StringHelper.isEmpty(resource.getComponent()))
            entry.setComponent(resource.getComponent());
        if (!StringHelper.isEmpty(resource.getTarget()))
            entry.setTarget(resource.getTarget());
        if (!StringHelper.isEmpty(resource.getUrl()))
            entry.setUrl(resource.getUrl());
        entry.setHidden(isYes(resource.getHidden()));
        entry.setKeepAlive(isYes(resource.getKeepAlive()));
        entry.setNoAuth(isYes(resource.getNoAuth()));
        if (!StringHelper.isEmpty(resource.getMetaConfig())) {
            entry.setMeta((Map<String, Object>) JsonTool.parseNonStrict(resource.getMetaConfig()));
        }

        if (!StringHelper.isEmpty(resource.getPropsConfig())) {
            entry.setProps((Map<String, Object>) JsonTool.parseNonStrict(resource.getPropsConfig()));
        }
        if (resource.getAuthCascadeUp() != null) {
            entry.setAuthCascadeUp(StringHelper.isYes(resource.getAuthCascadeUp()));
        }
        if (!StringHelper.isEmpty(resource.getDepends()))
            entry.setDepends(ConvertHelper.toCsvSet(resource.getDepends()));
        if (!StringHelper.isEmpty(resource.getPermissions()))
            entry.setPermissions(ConvertHelper.toCsvSet(resource.getPermissions()));
        entry.setStatus(resource.getStatus());
        return entry;
    }

    SiteMapBean makeSiteMap(String siteId) {
        SiteMapBean siteMap = allSiteMap.get(siteId);
        if (siteMap == null) {
            siteMap = new SiteMapBean();
            siteMap.setId(siteId);
            siteMap.setDisplayName(siteId);
            allSiteMap.put(siteId, siteMap);
        }
        return siteMap;
    }

    void initParentChildren() {
        for (SiteResourceBean resource : entryMap.values()) {
            if (resource.getParentId() != null) {
                addParentChild(resource.getParentId(), resource.getId());
            }
        }
    }

    void buildEntryTree(Set<String> visited) {
        for (Map.Entry<String, Set<String>> rootEntry : rootMenus.entrySet()) {
            SiteMapBean site = allSiteMap.get(rootEntry.getKey());
            for (String resourceId : rootEntry.getValue()) {
                SiteResourceBean menu = entryMap.get(resourceId);
                if (menu != null) {
                    site.addResource(menu);
                    buildEntryChild(menu, visited);
                }
            }
        }
    }

    void buildEntryChild(SiteResourceBean entry, Set<String> visited) {
        if (!visited.add(entry.getId())) {
            LOG.warn("nop.auth.site-map-tree-contains-loop:entryId={},entryName={}", entry.getId(),
                    entry.getDisplayName());
            return;
        }

        Set<String> entryIds = parentToChildren.get(entry.getId());
        if (entryIds != null) {
            for (String entryId : entryIds) {
                SiteResourceBean childEntry = entryMap.get(entryId);
                entry.addChild(childEntry);
                buildEntryChild(childEntry, visited);
            }
        }
    }
}
