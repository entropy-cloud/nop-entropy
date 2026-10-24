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
import io.nop.api.core.time.CoreMetrics;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_NOT_ALLOW_CHANGE;

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
    private final long version;

    private boolean frozen;

    /**
     * resource和lastModified用于缓存上次IResourceChangeChecker的检查结果。
     * 判据值为(lastModified, recordedAt)二元组，recordedAt为判据值的记录时刻（宽限锚）。
     * freeze后仍允许更新，且会被多个线程并发读写，因此声明为volatile
     */
    private final String path;
    private volatile long lastModified;
    private volatile long recordedAt;

    /**
     * 从资源路径到
     */
    protected Map<String, ResourceDependencySet> dependsMap;

    public ResourceDependencySet(IResourceReference resource) {
        this.path = resource.getPath();
        this.lastModified = resource.lastModified();
        this.recordedAt = CoreMetrics.currentTimeMillis();
        this.version = nextVersion();
    }

    protected ResourceDependencySet(ResourceDependencySet other) {
        this.version = other.version;
        this.path = other.path;
        this.lastModified = other.lastModified;
        this.recordedAt = other.recordedAt;
        if (other.dependsMap != null)
            this.dependsMap = new HashMap<>(other.dependsMap);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void freeze() {
        if (frozen)
            return;
        frozen = true;

        if (dependsMap != null) {
            for (ResourceDependencySet dep : dependsMap.values()) {
                dep.freeze();
            }
        }
    }

    protected void checkAllowChange() {
        if (frozen)
            throw new NopException(ERR_COMPONENT_NOT_ALLOW_CHANGE).param(ARG_RESOURCE_PATH, getResourcePath());
    }

    public boolean isMock() {
        return false;
    }

    public ResourceDependencySet copy() {
        return new ResourceDependencySet(this);
    }

    public long getLastModified() {
        return lastModified;
    }

    /**
     * 宽限锚：当前判据值(lastModified)的记录时刻。仅在lastModified值变化时重置
     */
    public long getRecordedAt() {
        return recordedAt;
    }

    /**
     * 判据值记录的唯一簿记入口：仅在lastModified值变化时重置宽限锚（重读同值不重锚，
     * 防止持续访问的热条目宽限窗口永续）。宽限锚语义见DefaultResourceChangeChecker
     */
    public void updateLastModified(long lastModified, long recordedAt) {
        if (this.lastModified != lastModified) {
            this.lastModified = lastModified;
            this.recordedAt = recordedAt;
        }
    }

    public void setLastModified(long lastModified) {
        updateLastModified(lastModified, CoreMetrics.currentTimeMillis());
    }

    public Map<String, ResourceDependencySet> getDependsMap() {
        return dependsMap;
    }

    public Map<String, ResourceDependencySet> makeDepends() {
        if (dependsMap == null)
            this.dependsMap = new HashMap<>();
        return this.dependsMap;
    }

    public Collection<ResourceDependencySet> getDepends() {
        if (dependsMap == null)
            return null;
        return dependsMap.values();
    }

    public Set<String> getDependPaths() {
        Set<String> ret = new HashSet<>();
        if (dependsMap != null) {
            for (ResourceDependencySet dep : dependsMap.values()) {
                ret.add(dep.getResourcePath());
            }
        }
        return ret;
    }

    public long getVersion() {
        return version;
    }

    public String getResourcePath() {
        return path;
    }

    public String toString() {
        return getResourcePath();
    }

    public void clear() {
        checkAllowChange();
        if (dependsMap != null)
            dependsMap.clear();
    }

    public void addDepend(ResourceDependencySet deps) {
        checkAllowChange();

        String path = deps.getResourcePath();
        if (this.path.equals(path)) {
            addDepends(deps.getDepends());
        } else {
            makeDepends().put(deps.getResourcePath(), deps);
        }
    }

    public ResourceDependencySet mergeWith(ResourceDependencySet deps) {
        if (this == deps)
            return this;

        ResourceDependencySet ret = deps.copy();
        ret.addDepends(this.getDepends());
        return ret;
    }

    public void addDepends(Collection<ResourceDependencySet> depends) {
        if (depends == null)
            return;

        for (ResourceDependencySet depend : depends) {
            addDepend(depend);
        }
    }
}