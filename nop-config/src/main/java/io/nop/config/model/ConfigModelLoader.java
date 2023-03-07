/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.model;

import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ConfigModelLoader {
    static final Logger LOG = LoggerFactory.getLogger(ConfigModelLoader.class);

    public ConfigModel loadConfigModel() {
        Map<String, ConfigVarModel> merged = new HashMap<>();

        ModuleManager.instance().getEnabledModuleIds().forEach(moduleId -> {
            Map<String, ConfigVarModel> moduleVars = loadModuleConfigVars(moduleId);
            if (moduleVars != null) {
                merge(merged, moduleVars);
            }
        });

        Map<String, ConfigVarModel> main = loadConfigVars("/main/conf/config.vars.yaml");
        if (main != null) {
            merge(merged, main);
        }

        ConfigModel model = new ConfigModel();
        model.setVars(merged);
        return model;
    }

    Map<String, ConfigVarModel> loadModuleConfigVars(String moduleId) {
        String path = '/' + moduleId + "/conf/config.vars.yaml";

        return loadConfigVars(path);
    }

    Map<String, ConfigVarModel> loadConfigVars(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (!resource.exists())
            return null;

        ConfigModel obj = JsonTool.loadDeltaBean(resource, ConfigModel.class, new DeltaJsonOptions());
        return obj.getVars();
    }

    private void merge(Map<String, ConfigVarModel> merged, Map<String, ConfigVarModel> map) {
        map.forEach((k, vl) -> {
            merge(merged, k, vl);
        });
    }

    void merge(Map<String, ConfigVarModel> merged, String key, ConfigVarModel vl) {
        ConfigVarModel old = merged.put(key, vl);
        if (old != null) {
            LOG.info("nop.core.exceptions.override-error-code-mapping:key={},loc={},oldLoc={}", key, vl.getLocation(),
                    old.getLocation());
        }
    }
}