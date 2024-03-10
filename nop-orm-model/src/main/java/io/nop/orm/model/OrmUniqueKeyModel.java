/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.orm.model._gen._OrmUniqueKeyModel;

import java.util.ArrayList;
import java.util.List;

public class OrmUniqueKeyModel extends _OrmUniqueKeyModel {
    private List<OrmColumnModel> columnModels;

    public OrmUniqueKeyModel() {

    }

    public void addColumn(String colName) {
        List<String> cols = getColumns();
        if (cols == null || cols.isEmpty()) {
            cols = new ArrayList<>();
        }
        if (!cols.contains(colName))
            cols.add(colName);
        setColumns(cols);
    }

    public List<OrmColumnModel> getColumnModels() {
        return columnModels;
    }

    public void setColumnModels(List<OrmColumnModel> columnModels) {
        this.columnModels = columnModels;
    }
}
