/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.resource;

import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.IComponentModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ComponentModelLoader;
import io.nop.core.resource.component.IComponentTransformer;
import io.nop.core.resource.component.IGeneratedComponent;
import io.nop.core.resource.component.IResourceComponentManager;
import io.nop.core.resource.deps.ResourceDependencySet;
import io.nop.idea.plugin.services.NopProjectService;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ProjectResourceComponentManager implements IResourceComponentManager {

    protected IResourceComponentManager getImpl() {
        return NopProjectService.get().getComponentManager();
    }

    @Override
    public ICancellable registerComponentModelConfig(ComponentModelConfig config) {
        return getImpl().registerComponentModelConfig(config);
    }

    @Override
    public void removeCachedModel(String path) {
        this.getImpl().removeCachedModel(path);
    }

    @Override
    public Runnable registerComponentModelLoader(String modelType, String fileType, IResourceObjectLoader<? extends IComponentModel> loader, boolean replace) {
        return getImpl().registerComponentModelLoader(modelType, fileType, loader, replace);
    }

    @Override
    public Runnable registerComponentModelTransformer(String fromModelType, String toModelType, IComponentTransformer<?, ?> transformer, boolean replace) {
        return getImpl().registerComponentModelTransformer(fromModelType, toModelType, transformer, replace);
    }

    @Override
    public ComponentModelConfig getModelConfigByModelPath(String path) {
        return getImpl().getModelConfigByModelPath(path);
    }

    @Override
    public ComponentModelLoader getComponentModelLoader(String path) {
        return getImpl().getComponentModelLoader(path);
    }

    @Override
    public void clearCache(String modelType) {
        getImpl().clearCache(modelType);
    }

    @Override
    public IComponentModel loadComponentModel(String modelPath) {
        return getImpl().loadComponentModel(modelPath);
    }

    @Override
    public IComponentModel loadComponentModel(String modelPath, String transform) {
        return getImpl().loadComponentModel(modelPath, transform);
    }

    @Override
    public IComponentModel loadComponentModelByUrl(String modelUrl) {
        return getImpl().loadComponentModelByUrl(modelUrl);
    }

    @Override
    public IComponentModel parseComponentModel(IResource resource) {
        return getImpl().parseComponentModel(resource);
    }

    @Override
    public IComponentModel parseComponentModel(IResource resource, String transform) {
        return getImpl().parseComponentModel(resource, transform);
    }

    @Override
    public String buildComponentPath(String modelPath, String genFormat) {
        return getImpl().buildComponentPath(modelPath, genFormat);
    }

    @Override
    public IGeneratedComponent loadGeneratedComponent(String componentPath) {
        return getImpl().loadGeneratedComponent(componentPath);
    }

    @Override
    public <T> T loadPrecompiledObject(String resourcePath) {
        return getImpl().loadPrecompiledObject(resourcePath);
    }

    @Override
    public boolean isDependencyChanged(String resourcePath) {
        return getImpl().isDependencyChanged(resourcePath);
    }

    @Override
    public <T> T collectDepends(String resourcePath, Supplier<T> task) {
        return getImpl().collectDepends(resourcePath, task);
    }

    @Override
    public <T> T collectDependsTo(ResourceDependencySet dep, Supplier<T> task) {
        return getImpl().collectDependsTo(dep, task);
    }

    @Override
    public <T> T ignoreDepends(Supplier<T> task) {
        return getImpl().ignoreDepends(task);
    }

    @Override
    public void traceDepends(String depResourcePath) {
        getImpl().traceDepends(depResourcePath);
    }

    @Override
    public <T> T runWhenDependsChanged(String resourcePath, Supplier<T> task) {
        return null;
    }

    @Override
    public void traceAllDepends(Collection<ResourceDependencySet> depends) {
        getImpl().traceAllDepends(depends);
    }

    @Override
    public ResourceDependencySet getResourceDepends(String resourcePath) {
        return getImpl().getResourceDepends(resourcePath);
    }

    @Override
    public ResourceDependencySet getModelDepends(String modelPath) {
        return getImpl().getModelDepends(modelPath);
    }

    @Override
    public String dumpDependsSet(ResourceDependencySet deps) {
        return getImpl().dumpDependsSet(deps);
    }

    @Override
    public void clearAllCache() {
        getImpl().clearAllCache();
    }

    @Override
    public boolean isAnyDependsChange(Collection<ResourceDependencySet> collection) {
        return getImpl().isAnyDependsChange(collection);
    }

    @Override
    public void clearDependencies() {
        getImpl().clearDependencies();
    }
}