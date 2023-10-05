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