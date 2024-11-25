/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_GLOBAL_THEME;

public class ThemeNamespaceHandler implements IResourceNamespaceHandler {
    public static final ThemeNamespaceHandler INSTANCE = new ThemeNamespaceHandler();

    private ICache<String, ThemeConfig> configCache = LocalCache.newCache("theme-config-cache", newConfig(-1),
            this::loadConfig);

    public static class ThemeConfig {
        private String inherits;

        public String getInherits() {
            return inherits;
        }

        public void setInherits(String inherits) {
            this.inherits = inherits;
        }
    }

    private ThemeConfig loadConfig(String path) {
        return JsonTool.loadBean(path, ThemeConfig.class);
    }

    @Override
    public String getNamespace() {
        return ResourceConstants.RESOURCE_NS_THEME;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        String path = ResourceHelper.removeNamespace(vPath, getNamespace());
        ResourceHelper.checkNormalVirtualPath(path);

        String theme = CFG_GLOBAL_THEME.get();
        if (StringHelper.isEmpty(theme)) {
            return locator.getResource(path);
        }

        return getThemeResource(theme, path, locator);
    }

    private IResource getThemeResource(String theme, String path, IResourceStore locator) {
        String resPath = "/_themes/" + theme + path;
        IResource resource = locator.getResource(resPath);
        if (resource.exists())
            return resource;

        String configPath = "/_themes/" + theme + "/config.json";
        ThemeConfig config = configCache.get(configPath);
        if (config != null) {
            String inherits = config.getInherits();
            if (StringHelper.isEmpty(inherits))
                return getThemeResource(inherits, path, locator);
        }

        return locator.getResource(path);
    }
}