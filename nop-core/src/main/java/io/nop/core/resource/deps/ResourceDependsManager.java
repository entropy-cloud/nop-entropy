/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.deps;

import io.nop.api.core.resource.IResourceReference;
import io.nop.commons.concurrent.thread.NamedThreadLocal;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.cache.IResourceLoadingCache;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.core.resource.component.IResourceDependencyManager;
import io.nop.core.resource.impl.UnknownResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 集中管理所有组件文件之间的依赖关系. 此对象的方法不应该被直接调用，应用代码应该通过 {@link io.nop.core.resource.component.IResourceComponentManager}接口来收集依赖。
 * <p>
 * 依赖管理只依赖IResourceReference接口，因此并不一定所有依赖都是文件。依赖集ResourceDependencySet可以序列化到缓存文件中。
 */
public class ResourceDependsManager implements IResourceDependencyManager, Closeable {
    static final Logger LOG = LoggerFactory.getLogger(ResourceDependsManager.class);

    private final IResourceChangeChecker changeChecker;

    private final ThreadLocal<ResourceDependsStack> dependsStack = new NamedThreadLocal<>("resource-depends-stack");

    private final Map<String, ResourceDependencySet> dependencyMap = new ConcurrentHashMap<>();

    private final IResourceLoadingCache<Object> cache = new ResourceLoadingCache<>("depends-with-cache", null, null);

    public ResourceDependsManager(IResourceChangeChecker changeChecker) {
        this.changeChecker = changeChecker;
    }

    @Override
    public void close() throws IOException {
        dependsStack.remove();
        dependencyMap.clear();
    }

    @Override
    public void clearDependencies() {
        dependencyMap.clear();
    }

    public void clearDependsStack() {
        dependsStack.remove();
    }

    ResourceDependsStack makeStack() {
        ResourceDependsStack stack = dependsStack.get();
        if (stack == null) {
            stack = new ResourceDependsStack();
            dependsStack.set(stack);
        }
        return stack;
    }

    /**
     * 记录依赖对象，并更新依赖集合
     *
     * @param resource 当前正在编译的资源文件
     * @param task     编译任务
     * @return task的执行结果
     */
    public <T> T collectDepends(IResourceReference resource, Supplier<T> task) {
        ResourceDependencySet current = currentDepends();
        ResourceDependsStack stack = makeStack();
        ResourceDependencySet deps = stack.push(resource);
        boolean success = false;
        try {
            T ret = task.get();
            if (current != null) {
                current.addDependencySet(deps);
            }
            deps.setLastModified(resource.lastModified());
            success = true;
            return ret;
        } finally {
            stack.pop();

            if (stack.isEmpty())
                dependsStack.remove();

            if (success) {
                stack.updateDepends(deps,dependencyMap);
            }
        }
    }

    @Override
    public <T> T collectDependsTo(ResourceDependencySet dep, Supplier<T> task) {
        ResourceDependsStack stack = makeStack();
        stack.push(dep);
        boolean success = false;
        try {
            T ret = task.get();
            success = true;
            return ret;
        } finally {
            stack.pop();
            if (stack.isEmpty())
                dependsStack.remove();

            if (success)
                stack.updateTo(dependencyMap);
        }
    }

    @Override
    public <T> T runWhenDependsChanged(String resourcePath, Supplier<T> task) {
        return (T) cache.get(resourcePath, p -> task.get());
    }

    /**
     * 忽略task执行过程中所访问到的组件，不把它们记录在当前编译组件的依赖集合中
     */
    public <T> T ignoreDepends(Supplier<T> task) {
        ResourceDependsStack stack = dependsStack.get();
        dependsStack.set(null); //NOSONAR
        try {
            return task.get();
        } finally {
            dependsStack.set(stack);
        }
    }


    public void addDependency(String resourcePath) {
        ResourceDependsStack stack = dependsStack.get();
        if (stack == null)
            return;

        ResourceDependencySet current = stack.current();

        ResourceDependencySet deps = stack.get(resourcePath);
        if (deps == null) {
            deps = makeDepends(stack, resourcePath);
        }
        if (deps != null)
            current.addDependency(deps.getResourcePath());
    }

    private ResourceDependencySet makeDepends(ResourceDependsStack stack, String path) {
        IResourceReference resource = changeChecker.resolveResource(path);
        if (resource == null)
            return null;
        return stack.make(resource);
    }

    @Override
    public ResourceDependencySet getResourceDepends(String resourcePath) {
        return dependencyMap.get(resourcePath);
    }

    public ResourceDependencySet currentDepends() {
        ResourceDependsStack stack = dependsStack.get();
        if (stack == null)
            return null;
        return stack.current();
    }

    @Override
    public boolean isDependencyChanged(String resourcePath) {
        return isDependencyChanged(resourcePath, new HashSet<>());
    }

    @Override
    public void traceDepends(String depResourcePath) {
        addDependency(depResourcePath);
    }

    public boolean isAnyDependsChange(Collection<String> depends) {
        return isAnyDependsChange(depends, new HashSet<>());
    }

    private boolean isAnyDependsChange(Collection<String> depends, Set<String> checkedResourcePaths) {
        if (depends != null) {
            for (String depResourcePath : depends) {
                if (isDependencyChanged(depResourcePath, checkedResourcePaths)) {
                    LOG.debug("nop.resource.depends-changed:path={}", depResourcePath);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断锁依赖的其他资源是否已经被改动。一个资源的依赖集合包含资源文件自身以及编译时所需要的其他资源文件
     *
     * @param checkedResourcePaths 为避免循环调用，通过checkedResourcePaths记录已经检查过的资源文件
     */
    private boolean isDependencyChanged(String resourcePath, Set<String> checkedResourcePaths) {
        if (!checkedResourcePaths.add(resourcePath)) {
            return false;
        }

        ResourceDependencySet deps = dependencyMap.get(resourcePath);
        if (deps == null) {
            return true;
        }

        ResourceChangeCheckResult result = changeChecker.checkChanged(deps.getResource(), deps.getLastModified());
        if (result.isChanged()) {
            return true;
        }

        // 内容没有修改，但是可能文件的修改时间变化了
        deps.setLastModified(result.getLastModified());

        for (String depResourcePath : deps.getDepends()) {
            if (isDependencyChanged(depResourcePath, checkedResourcePaths))
                return true;
        }
        return false;
    }

    public IResourceReference resolveResource(String resourcePath) {
        IResourceReference resource = changeChecker.resolveResource(resourcePath);
        if (resource == null)
            resource = new UnknownResource(resourcePath);
        return resource;
    }

    @Override
    public <T> T collectDepends(String resourcePath, Supplier<T> task) {
        if (currentDepends() == null || StringHelper.isEmpty(resourcePath)
                || ResourceConstants.RESOURCE_PATH_TEXT.equals(resourcePath))
            return task.get();

        IResourceReference resource = resolveResource(resourcePath);
        return collectDepends(resource, task);
    }
}