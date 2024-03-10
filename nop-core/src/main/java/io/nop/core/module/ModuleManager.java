/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.module;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static io.nop.core.CoreConfigs.CFG_MODULE_DISABLED_MODULE_NAMES;
import static io.nop.core.CoreConfigs.CFG_MODULE_ENABLED_MODULE_NAMES;

@GlobalInstance
public class ModuleManager {
    private static final Logger LOG = LoggerFactory.getLogger(ModuleManager.class);

    private static ModuleManager _instance = new ModuleManager();

    public static void registerInstance(ModuleManager instance) {
        _instance = instance;
    }

    private Map<String, ModuleModel> modules = Collections.emptyMap();

    public static ModuleManager instance() {
        return _instance;
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
        this.modules = modules;
    }

    public synchronized void updateDynModules(Map<String, ModuleModel> dynModules) {
        Guard.notNull(dynModules, "dynModules");

        Map<String, ModuleModel> modules = new TreeMap<>(this.modules);
        modules.entrySet().removeIf(entry -> {
            if (dynModules.containsKey(entry.getKey()))
                return false;
            // 删除已经不存在的动态模块
            return entry.getValue().isDynamic();
        });

        for (Map.Entry<String, ModuleModel> entry : dynModules.entrySet()) {
            entry.getValue().setDynamic(true);
            modules.put(entry.getKey(), entry.getValue());
        }

        Set<String> disabledModuleNames = CFG_MODULE_DISABLED_MODULE_NAMES.get();
        if (disabledModuleNames != null) {
            disabledModuleNames.forEach(modules::remove);
        }

        this.modules = modules;
    }

    public Map<String, ModuleModel> loadModules(Set<String> moduleNames) {
        Map<String, ModuleModel> ret = new HashMap<>();
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
        modules.clear();
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

    public Set<String> getEnabledModuleNames() {
        return modules.keySet();
    }

    public Collection<ModuleModel> getEnabledModules() {
        return modules.values();
    }

    public List<IResource> getAllModuleResources(String filePathInModule) {
        Collection<ModuleModel> modules = getEnabledModules();
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

    public IResource getModuleResource(String filePathInModule) {
        Collection<ModuleModel> modules = getEnabledModules();
        for (ModuleModel module : modules) {
            String path = StringHelper.appendPath('/' + module.getModuleId(), filePathInModule);
            IResource resource = VirtualFileSystem.instance().getResource(path);
            if (resource.exists()) {
                return resource;
            }
        }
        return null;
    }

    public List<IResource> findModuleResources(String filePathInModule, String suffix) {
        Collection<ModuleModel> modules = getEnabledModules();
        List<IResource> ret = new ArrayList<>();
        for (ModuleModel module : modules) {
            String path = StringHelper.appendPath('/' + module.getModuleId(), filePathInModule);
            Collection<? extends IResource> resources = VirtualFileSystem.instance().getAllResources(path, suffix);
            ret.addAll(resources);
        }
        return ret;
    }
}
