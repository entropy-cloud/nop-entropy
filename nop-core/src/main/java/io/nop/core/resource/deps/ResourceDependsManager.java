/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.deps;

import io.nop.commons.concurrent.thread.NamedThreadLocal;
import io.nop.api.core.resource.IResourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class ResourceDependsManager {
    static final Logger LOG = LoggerFactory.getLogger(ResourceDependsManager.class);

    private ThreadLocal<ResourceDependsStack> dependsStack = new NamedThreadLocal<>("resource-depends-stack");

    private Map<String, ResourceDependencySet> dependencyMap = new ConcurrentHashMap<>();

    public void clear() {
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
        addDependency(resource.getPath());
        ResourceDependsStack stack = makeStack();
        ResourceDependencySet deps = stack.push(resource);
        boolean success = false;
        try {
            T ret = task.get();
            success = true;
            return ret;
        } finally {
            stack.pop();
            if (success)
                updateDeps(deps);
        }
    }

    public <T> T collectDependsTo(ResourceDependencySet dep, Supplier<T> task) {
        ResourceDependsStack stack = makeStack();
        stack.push(dep);
        try {
            T ret = task.get();
            return ret;
        } finally {
            stack.pop();
            if (stack.isEmpty())
                dependsStack.set(null);
        }
    }

    /**
     * 忽略task执行过程中所访问到的组件，不把它们记录在当前编译组件的依赖集合中
     */
    public <T> T ignoreDepends(Supplier<T> task) {
        ResourceDependsStack stack = dependsStack.get();
        dependsStack.set(null);
        try {
            return task.get();
        } finally {
            dependsStack.set(stack);
        }
    }

    void updateDeps(ResourceDependencySet deps) {
        ResourceDependencySet oldDeps = dependencyMap.get(deps.getResourcePath());
        if (oldDeps == null) {
            dependencyMap.put(deps.getResourcePath(), deps);
        } else {
            // 如果缓存的版本号大于当前版本号，说明已经被其他线程修改。当前线程的执行结果已经是过期的结果，需要被放弃
            if (oldDeps.getVersion() < deps.getVersion()) {
                dependencyMap.put(deps.getResourcePath(), deps);
            }
        }
    }

    public void addDependency(String depResourcePath) {
        ResourceDependencySet deps = currentDepends();
        if (deps != null)
            deps.addDependency(depResourcePath);
    }

    public void addDependencies(Set<String> depends) {
        if (depends != null) {
            ResourceDependencySet deps = currentDepends();
            if (deps != null) {
                for (String depPath : depends) {
                    deps.addDependency(depPath);
                }
            }
        }
    }

    public ResourceDependencySet getDepends(String resourcePath) {
        return dependencyMap.get(resourcePath);
    }

    public ResourceDependencySet currentDepends() {
        ResourceDependsStack stack = dependsStack.get();
        if (stack == null)
            return null;
        return stack.current();
    }

    public boolean isAnyDependsChange(Set<String> depends, Set<String> checkedResourcePaths,
                                      IResourceDependsPersister defaultDependsLoader, IResourceChangeChecker checker) {
        if (depends != null) {
            for (String depResourcePath : depends) {
                if (isDependencyChanged(depResourcePath, checkedResourcePaths, defaultDependsLoader, checker))
                    return true;
            }
        }
        return false;
    }

    /**
     * 判断锁依赖的其他资源是否已经被改动。一个资源的依赖集合包含资源文件自身以及编译时所需要的其他资源文件
     *
     * @param checkedResourcePaths 为避免循环调用，通过checkedResourcePaths记录已经检查过的资源文件
     */
    public boolean isDependencyChanged(String resourcePath, Set<String> checkedResourcePaths,
                                       IResourceDependsPersister defaultDependsLoader, IResourceChangeChecker checker) {
        if (!checkedResourcePaths.add(resourcePath)) {
            return false;
        }

        ResourceDependencySet deps = dependencyMap.get(resourcePath);
        if (deps == null) {
            if (defaultDependsLoader != null) {
                try {
                    deps = defaultDependsLoader.loadDepends(resourcePath);
                } catch (Exception e) {
                    LOG.info("nop.core.component.load-default-depends-fail:resourcePath={}", resourcePath, e);
                }
            }
            if (deps == null) {
                return true;
            }

            dependencyMap.putIfAbsent(resourcePath, deps);
        }

        ResourceChangeCheckResult result = checker.checkChanged(resourcePath, deps.getLastModified(),
                deps.getResource());
        if (result.isChanged()) {
            return true;
        }

        // 内容没有修改，但是可能文件的修改时间变化了
        deps.setLastModified(result.getLastModified());
        deps.setResource(result.getResource());

        for (String depResourcePath : deps.getDepends()) {
            if (isDependencyChanged(depResourcePath, checkedResourcePaths, defaultDependsLoader, checker))
                return true;
        }
        return false;
    }

    public String dumpDependsSet(ResourceDependencySet deps) {
        StringBuilder sb = new StringBuilder();
        _dump(sb, deps, new HashSet<>(), 0);
        return sb.toString();
    }

    private void _dump(StringBuilder sb, ResourceDependencySet deps, Set<String> visited, int level) {
        for (String dep : deps.getDepends()) {
            indent(sb, level);
            sb.append(dep);

            if (visited.add(dep)) {
                ResourceDependencySet sub = this.getDepends(dep);
                if (sub != null) {
                    _dump(sb, sub, visited, level + 1);
                }
            } else {
                sb.append('*');
            }
        }
    }

    void indent(StringBuilder sb, int level) {
        sb.append('\n');
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }
}