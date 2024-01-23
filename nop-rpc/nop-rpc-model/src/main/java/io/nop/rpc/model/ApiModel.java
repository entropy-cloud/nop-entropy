package io.nop.rpc.model;

import io.nop.rpc.model._gen._ApiModel;

public class ApiModel extends _ApiModel implements IWithOptions {
    public ApiModel() {

    }

    public void addImportPath(String path) {
        ApiImportModel importModel = new ApiImportModel();
        importModel.setFrom(path);
        addImport(importModel);
    }

}
