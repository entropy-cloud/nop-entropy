/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.orm.model._gen._OrmUniqueKeyModel;

import java.util.List;

public class OrmUniqueKeyModel extends _OrmUniqueKeyModel {
    private List<OrmColumnModel> columnModels;

    public OrmUniqueKeyModel() {

    }

    public List<OrmColumnModel> getColumnModels() {
        return columnModels;
    }

    public void setColumnModels(List<OrmColumnModel> columnModels) {
        this.columnModels = columnModels;
    }
}
