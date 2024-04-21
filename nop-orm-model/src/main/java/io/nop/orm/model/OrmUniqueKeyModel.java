/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.commons.lang.ITagSetSupport;
import io.nop.orm.model._gen._OrmUniqueKeyModel;

import java.util.ArrayList;
import java.util.List;

public class OrmUniqueKeyModel extends _OrmUniqueKeyModel implements ITagSetSupport {
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

    public List<OrmColumnModel> getColumnModelsWithTenant(IEntityModel entityModel) {
        if (!entityModel.isUseTenant())
            return getColumnModels();

        boolean global = entityModel.containsTag(OrmModelConstants.TAG_NO_TENANT);
        if (global)
            return getColumnModels();

        IColumnModel tenantCol = entityModel.getTenantColumn();
        if (columnModels.contains(tenantCol))
            return columnModels;

        List<OrmColumnModel> ret = new ArrayList<>();
        ret.add((OrmColumnModel) tenantCol);
        ret.addAll(columnModels);
        return ret;
    }

    public List<OrmColumnModel> getColumnModels() {
        return columnModels;
    }

    public void setColumnModels(List<OrmColumnModel> columnModels) {
        this.columnModels = columnModels;
    }
}
