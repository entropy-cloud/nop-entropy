/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.module;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.UnknownResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.core.CoreConfigs.CFG_MODULE_DISABLED_MODULE_NAMES;
import static io.nop.core.CoreConfigs.CFG_MODULE_ENABLED_MODULE_NAMES;
import static io.nop.core.CoreErrors.ARG_OTHER_PATH;
import static io.nop.core.CoreErrors.ARG_PATH;
import static io.nop.core.CoreErrors.ARG_STD_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_MODULE_PATH_RESOLVE_TO_MULTI_FILE;

@GlobalInstance
public class ModuleManager {
    private static final Logger LOG = LoggerFactory.getLogger(ModuleManager.class);

    private static ModuleManager _INSTANCE = new ModuleManager();

    private final AtomicReference<Map<String, ModuleModel>> dynamicModules = new AtomicReference<>();

    public static void registerInstance(ModuleManager instance) {
        _INSTANCE = instance;
    }

    private final AtomicReference<Map<String, ModuleModel>> modules = new AtomicReference<>(new TreeMap<>());

    private ITenantModuleDiscovery tenantModuleDiscovery;

    public static ModuleManager instance() {
        return _INSTANCE;
    }

    public ITenantModuleDiscovery getTenantModuleDiscovery() {
        return tenantModuleDiscovery;
    }

    public void setTenantModuleDiscovery(ITenantModuleDiscovery tenantModuleDiscovery) {
        this.tenantModuleDiscovery = tenantModuleDiscovery;
    }

    /**
     * 在VirtualFileSystem初始化之后被调用
     */
    public void discover() {
        Set<String> enabledModuleNames = CFG_MODULE_ENABLED_MODULE_NAMES.get();
        Set<String> disabledModuleNames = CFG_MODULE_DISABLED_MODULE_NAMES.get();

        List<IResource> moduleFiles = VirtualFileSystem.instance().findAll("*/*/_module");

        Map<String, ModuleModel> modules = new TreeMap<>();

        for (IResource resource : moduleFiles) {
            String moduleName = ResourceHelper.getModuleName(resource.getStdPath());
            if (disabledModuleNames != null && disabledModuleNames.contains(moduleName)) {
                LOG.info("nop.core.ignore-disabled-module:moduleName={}", moduleName);
                continue;
            }
            if (enabledModuleNames != null && !enabledModuleNames.isEmpty() && !enabledModuleNames.contains(moduleName)) {
                LOG.info("nop.core.ignore-disabled-module:moduleName={}", moduleName);
                continue;
            }

            LOG.info("nop.core.add-module:moduleName={}", moduleName);
            ModuleModel module = loadModuleById(ResourceHelper.getModuleIdFromModuleName(moduleName));
            modules.put(moduleName, module);
        }
        this.modules.set(modules);
    }

    protected String getTenantId() {
        return ContextProvider.currentTenantId();
    }

    public Map<String, ModuleModel> loadModules(Set<String> moduleNames) {
        Map<String, ModuleModel> ret = new TreeMap<>();
        for (String moduleName : moduleNames) {
            String moduleId = ResourceHelper.getModuleIdFromModuleName(moduleName);
            IResource resource = VirtualFileSystem.instance().getResource("/" + moduleId + "/_module");
            if (resource.exists()) {
                ret.put(moduleName, loadModuleById(moduleId));
            }
        }
        return ret;
    }

    public void clear() {
        modules.set(new TreeMap<>());
    }

    private ModuleModel loadModuleById(String moduleId) {
        IResource configFile = VirtualFileSystem.instance().getResource("/" + moduleId + "/app.module.yaml");
        ModuleModel module;
        if (configFile.exists()) {
            module = JsonTool.parseBeanFromResource(configFile, ModuleModel.class);
        } else {
            module = new ModuleModel();
            module.setVersion("1.0");
        }
        module.setModuleId(moduleId);
        return module;
    }

    public Map<String, ModuleModel> getEnabledModuleMap(boolean includeTenant) {
        Map<String, ModuleModel> ret = new TreeMap<>();
        Map<String, ModuleModel> map = modules.get();
        if (map != null) {
            ret.putAll(map);
        }

        Map<String, ModuleModel> dynamics = dynamicModules.get();
        if (dynamics != null)
            ret.putAll(dynamics);

        if (includeTenant) {
            String tenantId = getTenantId();
            if (tenantId != null && tenantModuleDiscovery != null) {
                Map<String, ModuleModel> m = tenantModuleDiscovery.getEnabledTenantModules();
                if (m != null) {
                    ret.putAll(m);
                }
            }
        }
        return ret;
    }

    public Map<String, ModuleModel> getEnabledTenantModules() {
        if (tenantModuleDiscovery == null)
            return Collections.emptyMap();
        Map<String, ModuleModel> ret = tenantModuleDiscovery.getEnabledTenantModules();
        if (ret == null)
            ret = Collections.emptyMap();
        return ret;
    }

    public void updateDynamicModules(Map<String, ModuleModel> dynamicModules) {
        this.dynamicModules.set(dynamicModules);
    }

    public Collection<ModuleModel> getEnabledModules() {
        return getEnabledModules(true);
    }

    public Collection<ModuleModel> getEnabledModules(boolean includeTenant) {
        return getEnabledModuleMap(includeTenant).values();
    }

    public Set<String> getEnabledModuleNames(boolean includeTenant) {
        return getEnabledModuleMap(includeTenant).keySet();
    }

    public List<IResource> getAllModuleResources(boolean includeTenant, String filePathInModule) {
        return getAllModuleResourcesInModules(getEnabledModules(includeTenant), filePathInModule);
    }

    public List<IResource> findModuleResources(boolean includeTenant, String filePathInModule, String suffix) {
        return findModuleResourcesInModules(getEnabledModules(includeTenant), filePathInModule, suffix);
    }

    public IResource getModuleResource(boolean includeTenant, String filePathInModule) {
        return getModuleResourceInModules(getEnabledModules(includeTenant), filePathInModule);
    }

    public List<IResource> getAllModuleResourcesInModules(Collection<ModuleModel> modules, String filePathInModule) {
        List<IResource> ret = new ArrayList<>(modules.size());
        if (filePathInModule.startsWith("/"))
            filePathInModule = filePathInModule.substring(1);

        for (ModuleModel module : modules) {
            String path = '/' + module.getModuleId() + '/' + filePathInModule;
            IResource resource = VirtualFileSystem.instance().getResource(path);
            if (resource.exists()) {
                ret.add(resource);
            }
        }
        return ret;
    }

    public IResource getModuleResourceInModules(Collection<ModuleModel> modules, String filePathInModule) {
        IResource resource = null;
        for (ModuleModel module : modules) {
            String path = StringHelper.appendPath('/' + module.getModuleId(), filePathInModule);
            IResource moduleResource = VirtualFileSystem.instance().getResource(path, true);
            if (moduleResource != null) {
                if (AppConfig.isDebugMode()) {
                    if (resource != null)
                        throw new NopException(ERR_RESOURCE_MODULE_PATH_RESOLVE_TO_MULTI_FILE)
                                .param(ARG_STD_PATH, filePathInModule).param(ARG_PATH, resource.getPath())
                                .param(ARG_OTHER_PATH, moduleResource.getPath());
                    resource = moduleResource;
                    continue;
                }
                return moduleResource;
            }
        }
        if (resource != null)
            return resource;

        return new UnknownResource(filePathInModule);
    }

    public List<IResource> findModuleResourcesInModules(Collection<ModuleModel> modules, String filePathInModule, String suffix) {
        List<IResource> ret = new ArrayList<>();
        for (ModuleModel module : modules) {
            String path = StringHelper.appendPath('/' + module.getModuleId(), filePathInModule);
            Collection<? extends IResource> resources = VirtualFileSystem.instance().getAllResources(path, suffix);
            ret.addAll(resources);
        }
        return ret;
    }
}
