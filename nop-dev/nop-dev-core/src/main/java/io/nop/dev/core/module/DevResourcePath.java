/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dev.core.module;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class DevResourcePath {
    private String modelType;
    private String devResourcePath;

    private boolean exists;

    private String editorObjName;

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getDevResourcePath() {
        return devResourcePath;
    }

    public void setDevResourcePath(String devResourcePath) {
        this.devResourcePath = devResourcePath;
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public String getEditorObjName() {
        return editorObjName;
    }

    public void setEditorObjName(String editorObjName) {
        this.editorObjName = editorObjName;
    }
}