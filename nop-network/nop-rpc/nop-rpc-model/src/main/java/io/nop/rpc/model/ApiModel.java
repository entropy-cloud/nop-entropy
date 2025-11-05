/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
