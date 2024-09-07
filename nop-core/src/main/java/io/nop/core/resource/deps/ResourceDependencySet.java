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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 记录单个组件对象所依赖的所有资源
 */
public class ResourceDependencySet {
    static final AtomicLong s_next = new AtomicLong();

    public static long nextVersion() {
        return s_next.incrementAndGet();
    }

    /**
     * 每次发现修改，重新装载资源文件都会产生一个新版本号
     */
    private final long version = nextVersion();

    /**
     * resource和lastModified用于缓存上次IResourceChangeChecker的检查结果
     */
    private final IResourceReference resource;
    private long lastModified;

    protected final Set<String> depends = new HashSet<>();

    public ResourceDependencySet(IResourceReference resource) {
        this.resource = resource;
        this.lastModified = resource.lastModified();
    }

    public boolean isMock() {
        return false;
    }

    public ResourceDependencySet copy() {
        ResourceDependencySet ret = new ResourceDependencySet(resource);
        ret.depends.addAll(depends);
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

    public Set<String> getDepends() {
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

    public void addDependency(String path) {
        this.depends.add(path);
    }

    public void addDepends(Collection<String> depends) {
        if (depends == null)
            return;

        depends.addAll(depends);
    }
}