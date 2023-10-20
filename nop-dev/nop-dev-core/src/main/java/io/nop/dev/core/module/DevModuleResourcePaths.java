/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dev.core.module;

import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

@DataBean
public class DevModuleResourcePaths {
    private String moduleId;

    private String moduleName;
    private String rootPath;

    private List<DevResourcePath> modelPaths;

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public List<DevResourcePath> getModelPaths() {
        return modelPaths;
    }

    public void setModelPaths(List<DevResourcePath> modelPaths) {
        this.modelPaths = modelPaths;
    }

    public void addModelPath(DevResourcePath path) {
        if (modelPaths == null)
            modelPaths = new ArrayList<>();
        modelPaths.add(path);
    }
}