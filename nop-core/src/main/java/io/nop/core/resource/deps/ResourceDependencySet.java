/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.deps;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.resource.IResourceReference;

import java.util.HashSet;
import java.util.Set;

/**
 * 记录单个组件对象所依赖的所有资源
 */
@DataBean
public class ResourceDependencySet {
    /**
     * 每次依赖检查都使用一个新的版本号。因为依赖检查需要一定的时间，而且可能多个线程同时检查导致依赖更新。通过版本号的变化，可以识别出是否并发修改
     */
    private final long version;
    private final String resourcePath;

    /**
     * resource和lastModified用于缓存上次IResourceChangeChecker的检查结果
     */
    private IResourceReference resource;
    private long lastModified;

    private Set<String> depends = new HashSet<>();

    public ResourceDependencySet(@JsonProperty("resourcePath") String resourcePath,
                                 @JsonProperty("version") long version) {
        this.resourcePath = resourcePath;
        this.version = version;
    }

    @JsonIgnore
    public IResourceReference getResource() {
        return resource;
    }

    public void setResource(IResourceReference resource) {
        this.resource = resource;
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

    public void addDepends(Set<String> deps) {
        if (deps != null) {
            this.depends.addAll(deps);
        }
    }

    public void setDepends(Set<String> deps) {
        this.depends = deps;
    }

    public long getVersion() {
        return version;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String toString() {
        return resourcePath;
    }

    public void clear() {
        depends.clear();
    }

    public void addDependency(String depResourcePath) {
        if (depResourcePath != null)
            depends.add(depResourcePath);
    }
}