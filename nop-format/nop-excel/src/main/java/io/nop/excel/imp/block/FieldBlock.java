/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp.block;

import io.nop.excel.imp.model.ImportFieldModel;

public class FieldBlock extends BlockBase {

    private ImportFieldModel fieldModel;

    public ImportFieldModel getFieldModel() {
        return fieldModel;
    }

    public void setFieldModel(ImportFieldModel fieldModel) {
        this.fieldModel = fieldModel;
    }
}
