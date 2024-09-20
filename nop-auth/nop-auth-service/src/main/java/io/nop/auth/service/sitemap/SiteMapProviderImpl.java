/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.sitemap;

import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.SiteMapBean;
import io.nop.auth.api.messages.SiteResourceBean;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.model.ActionAuthModel;
import io.nop.auth.core.sitemap.ISiteMapProvider;
import io.nop.auth.dao.entity.NopAuthResource;
import io.nop.auth.dao.entity.NopAuthRoleResource;
import io.nop.auth.dao.entity.NopAuthSite;
import io.nop.auth.service.NopAuthConstants;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.IResourceLoadingCache;
import io.nop.core.resource.tenant.ResourceTenantManager;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.in;
import static io.nop.auth.dao.entity._gen._NopAuthRoleResource.PROP_NAME_roleId;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SITE_MAP_CACHE_MAX_SIZE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SITE_MAP_CACHE_TIMEOUT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SITE_MAP_STATIC_CONFIG_PATH;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SKIP_CHECK_FOR_ADMIN;
import static io.nop.auth.service.NopAuthConstants.RESOURCE_TYPE_SUB_MENU;
import static io.nop.auth.service.NopAuthConstants.RESOURCE_TYPE_TOP_MENU;
import static io.nop.auth.service.NopAuthConstants.ROLE_ADMIN;
import static io.nop.auth.service.NopAuthConstants.ROLE_NOP_ADMIN;

public class SiteMapProviderImpl implements ISiteMapProvider {

    @Inject
    protected IDaoProvider daoProvider;

    private boolean enableActionAuth;

    protected IResourceLoadingCache<SiteCacheData> siteCache;

    @PostConstruct
    public void init() {
        boolean useTenant = isUseTenant();
        this.siteCache = ResourceTenantManager.instance().makeLoadingCache("sitemap-cache", useTenant,
                this::loadSiteData, null, CFG_AUTH_SITE_MAP_CACHE_MAX_SIZE, CFG_AUTH_SITE_MAP_CACHE_TIMEOUT);
        GlobalCacheRegistry.instance().register(siteCache);
    }

    boolean isUseTenant() {
        return daoProvider.daoFor(NopAuthResource.class).isUseTenant();
    }

    @PreDestroy
    public void destroy() {
        if (siteCache != null)
            GlobalCacheRegistry.instance().unregister(siteCache);
    }

    @Override
    public void refreshCache() {
        siteCache.clear();
    }

    public void setEnableActionAuth(boolean enableActionAuth) {
        this.enableActionAuth = enableActionAuth;
    }

    @Override
    public Set<String> getRolesWithPermission(Set<String> permissions) {
        SiteCacheData cacheData = siteCache.get(I18nMessageManager.instance().getDefaultLocale());
        return cacheData.getRolesWithPermission(permissions);
    }

    @Override
    public SiteMapBean getSiteMap(String siteId, String locale) {
        locale = I18nMessageManager.instance().normalizeLocale(locale);

        return siteCache.get(locale).getSite(siteId);
    }

    SiteCacheData loadSiteData(String locale) {
        SiteCacheData data = new SiteCacheDataBuilder().addStaticConfigs(loadStaticSiteMap())
                .addDynamicConfig(getSites(), getResources(), getRoleResources()).build();

        normalizeI18n(data, locale);
        return data;
    }

    public List<SiteMapBean> loadStaticSiteMap() {
        String path = CFG_AUTH_SITE_MAP_STATIC_CONFIG_PATH.get();
        if (StringHelper.isEmpty(path))
            return Collections.emptyList();

        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (!resource.exists()) {
            if (resource.getPath().equals(NopAuthConstants.PATH_MAIN_ACTION_AUTH))
                return Collections.emptyList();
        }
        ActionAuthModel actionModel = (ActionAuthModel) new DslModelParser().parseFromResource(resource);
        return actionModel.getSites();
    }

    void normalizeI18n(SiteCacheData data, String locale) {
        for (SiteMapBean site : data.getSiteMaps().values()) {
            site.setLocale(locale);
            site.setDisplayName(
                    normalizeLabel("sites." + site.getId() + ".displayName", site.getDisplayName(), locale));

            List<SiteResourceBean> resources = site.getResources();
            if (resources != null) {
                for (SiteResourceBean resource : resources) {
                    normalizeI18n(resource, locale);
                }
            }
        }
    }

    void normalizeI18n(SiteResourceBean resource, String locale) {
        resource.setDisplayName(normalizeLabel("site.resource." + resource.getId() + ".displayName",
                resource.getDisplayName(), locale));

        List<SiteResourceBean> children = resource.getChildren();
        if (children != null) {
            for (SiteResourceBean child : children) {
                normalizeI18n(child, locale);
            }
        }
    }

    String normalizeLabel(String key, String label, String locale) {
        if (StringHelper.isEmpty(label))
            return label;

        if (label.startsWith(CoreConstants.I18N_VAR_PREFIX)) {
            return I18nMessageManager.instance().resolveI18nVar(locale, label);
        }
        String message = I18nMessageManager.instance().getMessage(locale, key, label);
        return message;
    }

    List<NopAuthSite> getSites() {
        IEntityDao<NopAuthSite> dao = daoProvider.daoFor(NopAuthSite.class);
        NopAuthSite example = new NopAuthSite();
        example.setStatus(DaoConstants.ACTIVE_STATUS_ACTIVE);
        List<NopAuthSite> allSys = dao.findAllByExample(example);
        return allSys;
    }

    List<NopAuthResource> getResources() {
        IEntityDao<NopAuthResource> dao = daoProvider.daoFor(NopAuthResource.class);
        List<NopAuthResource> resources = dao.findAll();
        return resources;
    }

    List<NopAuthRoleResource> getRoleResources() {
        IEntityDao<NopAuthRoleResource> dao = daoProvider.daoFor(NopAuthRoleResource.class);
        List<NopAuthRoleResource> resources = dao.findAll();
        return resources;
    }

    @Override
    public Set<String> getAllowedSiteEntries(String siteId, String userId, String deptId, Set<String> roleIds) {
        IEntityDao<NopAuthRoleResource> dao = daoProvider.daoFor(NopAuthRoleResource.class);
        QueryBean query = new QueryBean();
        query.addFilter(and(eq("resource.siteId", siteId), in(PROP_NAME_roleId, roleIds)));
        return dao.findAllByQuery(query).stream().map(NopAuthRoleResource::getResourceId).collect(Collectors.toSet());
    }

    @Override
    public SiteMapBean filterAllowedMenu(SiteMapBean site, String userId, String deptId, Set<String> roleIds,
                                         boolean includeFunctionPoints) {
        site = site.deepClone();
        if (site.getLocale() == null)
            site.setLocale(I18nMessageManager.instance().getDefaultLocale());

        if (roleIds == null)
            roleIds = Collections.emptySet();

        SiteCacheData cache = siteCache.get(site.getLocale());
        site.removePermissions();

        if (enableActionAuth) {
            if (!isSkipForAdmin(roleIds))
                applyAuthFilter(site.getResources(), cache.getResourceToRoles(), roleIds);

            if (!includeFunctionPoints)
                site.removeFunctionPoints();
            site.removeInactive();
        } else {
            if (!includeFunctionPoints)
                site.removeFunctionPoints();
        }
        return site;
    }

    boolean isSkipForAdmin(Set<String> roleIds) {
        if (!CFG_AUTH_SKIP_CHECK_FOR_ADMIN.get())
            return false;

        return roleIds.contains(ROLE_ADMIN) || roleIds.contains(ROLE_NOP_ADMIN);
    }

    void applyAuthFilter(List<SiteResourceBean> resources, Map<String, Set<String>> resourceToRoles,
                         Set<String> roleIds) {
        if (resources == null)
            return;

        for (SiteResourceBean resource : resources) {
            if (resource.isNoAuth())
                continue;

            Set<String> roles = resourceToRoles.get(resource.getId());
            if (!containsRole(roles, roleIds)) {
                resource.setStatus(AuthApiConstants.RESOURCE_STATUS_DISABLED);
            }

            applyAuthFilter(resource.getChildren(), resourceToRoles, roleIds);
        }
    }

    boolean isMenu(SiteResourceBean resource) {
        String type = resource.getResourceType();
        return RESOURCE_TYPE_TOP_MENU.equals(type) || RESOURCE_TYPE_SUB_MENU.equals(type);
    }

    boolean containsRole(Set<String> authRoles, Set<String> roleIds) {
        if (authRoles == null || authRoles.isEmpty())
            return false;

        // 总是允许user角色
        if (authRoles.contains(AuthCoreConstants.ROLE_USER))
            return true;

        for (String roleId : roleIds) {
            if (authRoles.contains(roleId))
                return true;
        }
        return false;
    }

    public boolean isPermitted(String permission, ISecurityContext context) {
        SiteCacheData cacheData = siteCache.get(I18nMessageManager.instance().getDefaultLocale());
        return cacheData.isPermitted(permission, context);
    }
}