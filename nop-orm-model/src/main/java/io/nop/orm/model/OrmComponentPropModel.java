/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.orm.model._gen._OrmComponentPropModel;

public class OrmComponentPropModel extends _OrmComponentPropModel implements IEntityComponentPropModel {
    private OrmColumnModel columnModel;

    public OrmComponentPropModel() {

    }

    @Override
    public int getColumnPropId() {
        return columnModel.getPropId();
    }

    public OrmColumnModel getColumnModel() {
        return columnModel;
    }

    public void setColumnModel(OrmColumnModel columnModel) {
        this.columnModel = columnModel;
    }
}
