/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.orm.model._gen._OrmIndexColumnModel;

public class OrmIndexColumnModel extends _OrmIndexColumnModel {
    private OrmColumnModel columnModel;

    public OrmIndexColumnModel() {

    }

    public OrmColumnModel getColumnModel() {
        return columnModel;
    }

    public void setColumnModel(OrmColumnModel columnModel) {
        this.columnModel = columnModel;
    }

    public String getColumnCode() {
        return columnModel.getCode();
    }
}
