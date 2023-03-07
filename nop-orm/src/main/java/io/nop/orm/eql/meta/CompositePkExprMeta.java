/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.core.lang.sql.binder.IDataParameterBinder;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IOrmDataType;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.support.OrmEntityHelper;

import java.util.List;

public class CompositePkExprMeta implements ISqlExprMeta {
    private final List<IDataParameterBinder> columnBinders;
    private final IEntityPropModel propModel;
    private final List<String> columnNames;

    public CompositePkExprMeta(List<String> columnNames, List<IDataParameterBinder> columnBinders,
                               IEntityPropModel propModel) {
        this.columnBinders = columnBinders;
        this.propModel = propModel;
        this.columnNames = columnNames;
    }

    @Override
    public List<String> getColumnNames() {
        return columnNames;
    }

    @Override
    public int getColumnCount() {
        return columnBinders.size();
    }

    @Override
    public List<IDataParameterBinder> getColumnBinders() {
        return columnBinders;
    }

    @Override
    public IOrmDataType getOrmDataType() {
        return propModel;
    }

    @Override
    public Object buildValue(Object[] row, int fromIndex, IOrmSessionImplementor session) {
        Object[] id = new Object[getColumnCount()];
        System.arraycopy(row, fromIndex, id, 0, id.length);
        return OrmEntityHelper.castId(propModel.getOwnerEntityModel(), id);
    }
}
