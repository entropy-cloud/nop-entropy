/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class VfsConfig implements Serializable {
    private static final long serialVersionUID = -8485809092971561445L;

    /**
     * 从basePath映射到实际路径。 例如 /nop/oa/ --> /home/oa/store 表示虚拟路径/nop/oa/test.html实际存放在 /home/oa/store/test.html
     */
    private Map<String, String> pathMappings;

    /**
     * 资源jar包，合并所有_vfs目录下的文件，作为虚拟文件系统的一部分。排在前面的jar包优先级高，将会自动屏蔽后续jar包中的同名文件。
     */
    private List<String> libPaths;

    private List<String> deltaLayerIds;

    /**
     * 扫描classpath下_vfs目录，作为虚拟文件系统的一部分
     */
    private boolean scanClassPath = true;

    private String storeBuilderClass = DeltaResourceStoreBuilder.class.getName();

    public String getStoreBuilderClass() {
        return storeBuilderClass;
    }

    public void setStoreBuilderClass(String storeBuilderClass) {
        this.storeBuilderClass = storeBuilderClass;
    }

    public Map<String, String> getPathMappings() {
        return pathMappings;
    }

    public void setPathMappings(Map<String, String> pathMappings) {
        this.pathMappings = pathMappings;
    }

    public List<String> getDeltaLayerIds() {
        return deltaLayerIds;
    }

    public void setDeltaLayerIds(List<String> deltaLayerIds) {
        this.deltaLayerIds = deltaLayerIds;
    }

    public boolean isScanClassPath() {
        return scanClassPath;
    }

    public void setScanClassPath(boolean scanClassPath) {
        this.scanClassPath = scanClassPath;
    }

    public List<String> getLibPaths() {
        return libPaths;
    }

    public void setLibPaths(List<String> libPaths) {
        this.libPaths = libPaths;
    }
}