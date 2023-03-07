/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.store;

import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.WfModel;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ResourceWorkflowModelStore implements IWorkflowModelStore {
    private final ResourceLoadingCache<WfModel> wfModelCache = new ResourceLoadingCache<>(
            "wf-model-cache", this::parseModel, null);

    private boolean registerGlobalCache = true;

    public boolean isRegisterGlobalCache() {
        return registerGlobalCache;
    }

    public void setRegisterGlobalCache(boolean registerGlobalCache) {
        this.registerGlobalCache = registerGlobalCache;
    }

    @PostConstruct
    public void init() {
        if (registerGlobalCache) {
            GlobalCacheRegistry.instance().register(wfModelCache);
        }
    }

    @PreDestroy
    public void destroy() {
        GlobalCacheRegistry.instance().unregister(wfModelCache);
    }

    private WfModel parseModel(String key) {
        int pos = key.indexOf('|');
        String wfName = key.substring(0, pos);
        String wfVersion = key.substring(pos + 1);

        IResource resource = getModelResource(wfName, wfVersion);
        return WfModelParser.parseWorkflowModel(resource);
    }

    @Override
    public String getLatestVersion(String wfName) {
        List<IResource> resources = ModuleManager.instance().findModuleResources("/wf/" + wfName, ".xwf");
        Collections.sort(resources, Comparator.comparing(IResource::getName));
        if (resources.isEmpty())
            return null;
        IResource resource = resources.get(resources.size() - 1);
        return StringHelper.removeFileExt(resource.getName());
    }

    @Override
    public List<String> getAllVersions(String wfName) {
        List<IResource> resources = ModuleManager.instance().findModuleResources("/wf/" + wfName, ".xwf");
        Collections.sort(resources, Comparator.comparing(IResource::getName));
        if (resources.isEmpty())
            return Collections.emptyList();
        return resources.stream().map(resource -> StringHelper.removeFileExt(resource.getName())).collect(Collectors.toList());
    }

    @Override
    public IResource getModelResource(String wfName, String wfVersion) {
        wfVersion = normalizeVersion(wfName, wfVersion);
        String path = "module:/wf/" + wfName + "/" + wfVersion + ".xwf";

        return VirtualFileSystem.instance().getResource(path);
    }

    private String normalizeVersion(String wfName, String wfVersion) {
        if (wfVersion == null) {
            wfVersion = getLatestVersion(wfName);
            if (wfVersion == null)
                wfVersion = "1.0";
        }
        return wfVersion;
    }

    @Override
    public IWorkflowModel getWorkflowModel(String wfName, String wfVersion) {
        wfVersion = normalizeVersion(wfName, wfVersion);

        return wfModelCache.get(buildModelKey(wfName, wfVersion));
    }

    String buildModelKey(String wfName, String wfVersion) {
        return wfName + "|" + wfVersion;
    }

    @Override
    public void removeModelCache(String wfName, String wfVersion) {
        if (wfVersion == null) {
            List<String> versions = getAllVersions(wfName);
            for (String version : versions) {
                wfModelCache.remove(buildModelKey(wfName, version));
            }
        } else {
            wfModelCache.remove(buildModelKey(wfName, wfVersion));
        }
    }
}
