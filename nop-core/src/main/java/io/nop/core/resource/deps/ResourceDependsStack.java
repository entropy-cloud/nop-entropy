/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.deps;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.util.Guard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreConfigs.CFG_RESOURCE_MAX_DEPS_STACK_SIZE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ARG_ROOT_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_EXCEED_MAX_DEPS_STACK_SIZE;

public class ResourceDependsStack {
    private final long version = ResourceDependencySet.nextVersion();

    private final Map<String, ResourceDependencySet> depMap = new HashMap<>();
    private final List<ResourceDependencySet> depStack = new ArrayList<>();

    public boolean isEmpty() {
        return depStack.isEmpty();
    }

    public long getVersion() {
        return version;
    }

    public void updateTo(Map<String, ResourceDependencySet> map) {
        depMap.forEach((path, dep) -> {
            // stack创建表示本次请求开始，如果发现资源文件修改，则整体更新。否则有可能是多种途径都发现同一个资源文件被修改，此时需要合并依赖
            updateDepends(dep, map);
        });
    }

    public void updateDepends(ResourceDependencySet dep, Map<String, ResourceDependencySet> map) {
        map.merge(dep.getResourcePath(), dep, (old, newDep) -> {
            if (old.getVersion() > version)
                return old;

            ResourceDependencySet ret;
            if (old.getVersion() < version) {
                ret = newDep.copy();
            } else {
                ret = newDep.mergeWith(old);
            }
            ret.freeze();
            return ret;
        });
    }

    public ResourceDependencySet push(IResourceReference resource) {
        ResourceDependencySet dep = make(resource);
        push(dep);
        return dep;
    }

    public ResourceDependencySet make(IResourceReference resource) {
        String resourcePath = resource.getPath();
        ResourceDependencySet dep = depMap.get(resourcePath);
        if (dep == null || dep.isFrozen()) {
            dep = new ResourceDependencySet(resource);
            depMap.put(resourcePath, dep);
        }
        return dep;
    }

    public void traceDepends(Collection<ResourceDependencySet> deps) {
        ResourceDependencySet current = current();
        if (current == null)
            return;

        for (ResourceDependencySet dep : deps) {
            ResourceDependencySet old = depMap.get(dep.getResourcePath());
            if (old != null) {
                current.addDepend(old);
            } else {
                current.addDepend(dep);
            }
        }
    }

    public void add(ResourceDependencySet dep) {
        depMap.put(dep.getResourcePath(), dep);
    }

    public ResourceDependencySet get(String path) {
        return depMap.get(path);
    }

    public void push(ResourceDependencySet dep) {
        if (depStack.size() >= CFG_RESOURCE_MAX_DEPS_STACK_SIZE.get())
            throw new NopException(ERR_RESOURCE_EXCEED_MAX_DEPS_STACK_SIZE).param(ARG_RESOURCE_PATH, dep.getResourcePath())
                    .param(ARG_ROOT_PATH, depStack.get(0).getResourcePath());

        Guard.checkArgument(!dep.isFrozen(), "dependency is frozen");

        depStack.add(dep);
    }

    public ResourceDependencySet current() {
        if (depStack.isEmpty())
            return null;
        ResourceDependencySet deps = depStack.get(depStack.size() - 1);
        return deps;
    }

    public void pop() {
        depStack.remove(depStack.size() - 1);
    }
}