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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreConfigs.CFG_RESOURCE_MAX_DEPS_STACK_SIZE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ARG_ROOT_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_EXCEED_MAX_DEPS_STACK_SIZE;

public class ResourceDependsStack {
    private Map<String, ResourceDependencySet> depMap = new HashMap<>();
    // Set<String> depSet = new HashSet<>();
    private List<ResourceDependencySet> depStack = new ArrayList<>();

    public boolean isEmpty() {
        return depStack.isEmpty();
    }

    public ResourceDependencySet push(IResourceReference resource) {
        if (depStack.size() >= CFG_RESOURCE_MAX_DEPS_STACK_SIZE.get())
            throw new NopException(ERR_RESOURCE_EXCEED_MAX_DEPS_STACK_SIZE).param(ARG_RESOURCE_PATH, resource.getPath())
                    .param(ARG_ROOT_PATH, depStack.get(0).getResourcePath());
        // depSet.add(resourcePath);
        // if (!depSet.add(resourcePath)) {
        // throw new NopException(ERR_COMPONENT_DEP_STACK_CONTAINS_LOOP)
        // .param(ARG_DEP_STACK, depStack).param(ARG_RESOURCE_PATH,resourcePath);
        // }
        ResourceDependencySet dep = make(resource);
        depStack.add(dep);
        return dep;
    }

    public ResourceDependencySet make(IResourceReference resource) {
        String resourcePath = resource.getPath();
        ResourceDependencySet dep = depMap.get(resourcePath);
        if (dep == null) {
            dep = new ResourceDependencySet(resource);
            depMap.put(resourcePath, dep);
        }
        return dep;
    }

    public ResourceDependencySet get(String path) {
        return depMap.get(path);
    }

    public void push(ResourceDependencySet dep) {
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