/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.exceptions;

import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.type.utils.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ErrorCodeMappingsLoader {
    static final Logger LOG = LoggerFactory.getLogger(ErrorCodeMappingsLoader.class);

    public Map<String, ErrorCodeMapping> loadErrorCodeMappings() {
        Map<String, ErrorCodeMapping> merged = new HashMap<>();

        ModuleManager.instance().getEnabledModules().forEach(module -> {
            Map<String, ErrorCodeMapping> moduleErrors = loadModuleErrors(module.getModuleId());
            if (moduleErrors != null) {
                merge(merged, moduleErrors);
            }
        });

        Map<String, ErrorCodeMapping> main = loadErrors("/main/conf/app.errors.yaml");
        if (main != null) {
            merge(merged, main);
        }

        return merged;
    }

    Map<String, ErrorCodeMapping> loadModuleErrors(String moduleId) {
        String path = '/' + moduleId + "/conf/app.errors.yaml";

        return loadErrors(path);
    }

    Map<String, ErrorCodeMapping> loadErrors(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (!resource.exists())
            return null;

        Map<String, ErrorCodeMapping> obj = JsonTool.loadDeltaBean(resource,
                new TypeReference<Map<String, ErrorCodeMapping>>() {
                }.getType(), new DeltaJsonOptions());
        return obj;
    }

    private void merge(Map<String, ErrorCodeMapping> merged, Map<String, ErrorCodeMapping> map) {
        map.forEach((k, vl) -> {
            merge(merged, k, vl);
        });
    }

    void merge(Map<String, ErrorCodeMapping> merged, String key, ErrorCodeMapping vl) {
        ErrorCodeMapping old = merged.put(key, vl);
        if (old != null) {
            LOG.info("nop.core.exceptions.override-error-code-mapping:key={},loc={},oldLoc={}", key, vl.getLocation(),
                    old.getLocation());
        }
    }
}