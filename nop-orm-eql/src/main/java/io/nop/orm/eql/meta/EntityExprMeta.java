/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.commons.collections.IntArray;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.eql.IEqlQueryContext;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmDataType;
import io.nop.orm.eql.utils.EqlHelper;

import java.util.ArrayList;
import java.util.List;

public class EntityExprMeta implements ISqlExprMeta {
    private final IEntityModel entityModel;
    private final IntArray propIds;
    private final List<IDataParameterBinder> columnBinders;
    private final List<String> columnNames;

    public EntityExprMeta(IDialect dialect, IEntityModel entityModel, IntArray propIds,
                          List<IDataParameterBinder> columnBinders) {
        this.entityModel = entityModel;
        this.propIds = propIds;
        this.columnBinders = columnBinders;
        this.columnNames = buildColumnNames(dialect, entityModel, propIds);
    }

    List<String> buildColumnNames(IDialect dialect, IEntityModel entityModel, IntArray propIds) {
        List<String> ret = new ArrayList<>(propIds.size());
        for (int propId : propIds) {
            IColumnModel col = entityModel.getColumnByPropId(propId, false);
            String colName = EqlHelper.getColumnName(dialect, col);
            ret.add(colName);
        }
        return ret;
    }

    public String getEntityName(){
        return entityModel.getName();
    }

    @Override
    public List<String> getColumnNames() {
        return columnNames;
    }

    public IEntityModel getEntityModel() {
        return entityModel;
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
        return entityModel;
    }

    @Override
    public Object buildValue(Object[] row, int fromIndex, IEqlQueryContext session) {
        Object id = session.internalReadId(row, fromIndex, entityModel);
        Object[] values = new Object[columnBinders.size()];
        System.arraycopy(row, fromIndex, values, 0, columnBinders.size());
//        IOrmEntity entity = session.internalLoad(entityModel.getName(), id);
//        session.internalAssemble(entity, values, propIds);
        IDaoEntity entity = session.internalMakeEntity(entityModel.getName(), id,
                values, propIds);
        return entity;
    }
}
