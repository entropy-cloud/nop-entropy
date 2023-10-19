/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.store;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.core.resource.component.ResourceVersionHelper;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.WfModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

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
        Long wfVersion = ConvertHelper.toLong(key.substring(pos + 1));

        IResource resource = getModelResource(wfName, wfVersion);
        return WfModelParser.parseWorkflowModel(resource);
    }

    @Override
    public Long getLatestVersion(String wfName) {
        List<IResource> resources = ModuleManager.instance().findModuleResources("/wf/" + wfName, ".xwf");
        Collections.sort(resources, Comparator.comparing(IResource::getName));
        if (resources.isEmpty())
            return null;
        IResource resource = resources.get(resources.size() - 1);
        return ResourceVersionHelper.getNumberVersion(resource.getName());
    }

    @Override
    public List<Long> getAllVersions(String wfName) {
        List<IResource> resources = ModuleManager.instance().findModuleResources("/wf/" + wfName, ".xwf");
        Collections.sort(resources, Comparator.comparing(IResource::getName));
        if (resources.isEmpty())
            return Collections.emptyList();
        return resources.stream().map(resource -> ResourceVersionHelper.getNumberVersion(resource.getName())).collect(Collectors.toList());
    }

    @Override
    public IResource getModelResource(String wfName, Long wfVersion) {
        wfVersion = normalizeVersion(wfName, wfVersion);
        String path = "module:/wf/" + wfName + "/" + wfVersion + ".xwf";

        return VirtualFileSystem.instance().getResource(path);
    }

    private Long normalizeVersion(String wfName, Long wfVersion) {
        if (wfVersion == null || wfVersion <= 0) {
            wfVersion = getLatestVersion(wfName);
            if (wfVersion == null)
                wfVersion = 1L;
        }
        return wfVersion;
    }

    @Override
    public IWorkflowModel getWorkflowModel(String wfName, Long wfVersion) {
        wfVersion = normalizeVersion(wfName, wfVersion);

        return wfModelCache.get(buildModelKey(wfName, wfVersion));
    }

    String buildModelKey(String wfName, Long wfVersion) {
        return wfName + "|" + wfVersion;
    }

    @Override
    public void removeModelCache(String wfName, Long wfVersion) {
        if (wfVersion == null) {
            List<Long> versions = getAllVersions(wfName);
            for (Long version : versions) {
                wfModelCache.remove(buildModelKey(wfName, version));
            }
        } else {
            wfModelCache.remove(buildModelKey(wfName, wfVersion));
        }
    }
}
