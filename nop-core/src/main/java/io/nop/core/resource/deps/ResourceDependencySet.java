/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.deps;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.resource.IResourceReference;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 记录单个组件对象所依赖的所有资源
 */
public class ResourceDependencySet {
    static final AtomicLong s_next = new AtomicLong();

    /**
     * 每次发现修改，重新装载资源文件都会产生一个新版本号
     */
    private final long version = s_next.incrementAndGet();

    /**
     * resource和lastModified用于缓存上次IResourceChangeChecker的检查结果
     */
    private final IResourceReference resource;
    private long lastModified;

    protected final Map<String, Long> depends = new HashMap<>();
    private final Map<String, ResourceDependencySet> depSets = new HashMap<>();

    public ResourceDependencySet(IResourceReference resource) {
        this.resource = resource;
        this.lastModified = resource.lastModified();
    }

    public ResourceDependencySet copy() {
        ResourceDependencySet ret = new ResourceDependencySet(resource);
        ret.depends.putAll(depends);
        return ret;
    }

    public void refreshLastModified() {
        this.lastModified = resource.lastModified();
    }

    @JsonIgnore
    public IResourceReference getResource() {
        return resource;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public Map<String, Long> getDepends() {
        return depends;
    }

    public long getVersion() {
        return version;
    }

    public String getResourcePath() {
        return resource.getPath();
    }

    public String toString() {
        return getResourcePath();
    }

    public void clear() {
        depends.clear();
    }

    public void addDependency(ResourceDependencySet resource) {
        String path = resource.getResourcePath();
        depends.put(path, resource.getVersion());
        depSets.put(path, resource);
    }

    public ResourceDependencySet getDependsSet(String path) {
        return depSets.get(path);
    }

    public void addDepends(Map<String, Long> depends) {
        this.depends.putAll(depends);
    }
}