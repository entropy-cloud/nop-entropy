/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.module;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static io.nop.core.CoreConfigs.CFG_MODULE_DISABLED_MODULE_IDS;
import static io.nop.core.CoreConfigs.CFG_MODULE_ENABLED_MODULE_IDS;

@GlobalInstance
public class ModuleManager {
    static final Logger LOG = LoggerFactory.getLogger(ModuleManager.class);

    static final ModuleManager _instance = new ModuleManager();

    private Map<String, ModuleModel> modules = new TreeMap<>();

    public static ModuleManager instance() {
        return _instance;
    }

    /**
     * 在VirtualFileSystem初始化之后被调用
     */
    public void discover() {
        Set<String> enabledModuleIds = CFG_MODULE_ENABLED_MODULE_IDS.get();
        Set<String> disabledModuleIds = CFG_MODULE_DISABLED_MODULE_IDS.get();

        List<IResource> moduleFiles = VirtualFileSystem.instance().findAll("*/*/_module");

        for (IResource resource : moduleFiles) {
            String moduleId = ResourceHelper.getModuleId(resource.getStdPath());
            if (disabledModuleIds != null && disabledModuleIds.contains(moduleId)) {
                LOG.info("nop.core.ignore-disabled-module:moduleId={}", moduleId);
                continue;
            }
            if (enabledModuleIds != null && enabledModuleIds.size() > 0 && !enabledModuleIds.contains(moduleId)) {
                LOG.info("nop.core.ignore-disabled-module:moduleId={}", moduleId);
                continue;
            }

            LOG.info("nop.core.add-module:moduleId={}", moduleId);
            loadModule(moduleId, resource);
        }
    }

    public void clear() {
        modules.clear();
    }

    private void loadModule(String moduleId, IResource moduleFile) {
        IResource configFile = ResourceHelper.resolveSibling(moduleFile, "app.module.yaml");
        if (configFile.exists()) {
            ModuleModel model = JsonTool.parseBeanFromResource(configFile, ModuleModel.class);
            modules.put(moduleId, model);
        } else {
            ModuleModel model = new ModuleModel();
            model.setVersion("1.0");
            modules.put(moduleId, model);
        }
    }

    public Set<String> getEnabledModuleIds() {
        return modules.keySet();
    }

    public List<IResource> getAllModuleResources(String filePathInModule) {
        Set<String> moduleIds = getEnabledModuleIds();
        List<IResource> ret = new ArrayList<>(moduleIds.size());
        for (String moduleId : moduleIds) {
            String path = StringHelper.appendPath('/' + moduleId, filePathInModule);
            IResource resource = VirtualFileSystem.instance().getResource(path);
            if (resource.exists()) {
                ret.add(resource);
            }
        }
        return ret;
    }

    public IResource getModuleResource(String filePathInModule) {
        Set<String> moduleIds = getEnabledModuleIds();
        for (String moduleId : moduleIds) {
            String path = StringHelper.appendPath('/' + moduleId, filePathInModule);
            IResource resource = VirtualFileSystem.instance().getResource(path);
            if (resource.exists()) {
                return resource;
            }
        }
        return null;
    }

    public List<IResource> findModuleResources(String filePathInModule, String suffix) {
        Set<String> moduleIds = getEnabledModuleIds();
        List<IResource> ret = new ArrayList<>();
        for (String moduleId : moduleIds) {
            String path = StringHelper.appendPath('/' + moduleId, filePathInModule);
            Collection<? extends IResource> resources = VirtualFileSystem.instance().getAllResources(path, suffix);
            ret.addAll(resources);
        }
        return ret;
    }
}
