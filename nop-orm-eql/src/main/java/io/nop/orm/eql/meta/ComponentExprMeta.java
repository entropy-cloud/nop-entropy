/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.commons.collections.IntArray;
import io.nop.commons.collections.MutableIntArray;
import io.nop.dao.api.IDaoEntity;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.IEqlQueryContext;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityComponentModel;
import io.nop.orm.model.IOrmDataType;
import io.nop.orm.eql.utils.EqlHelper;

import java.util.ArrayList;
import java.util.List;

public class ComponentExprMeta implements ISqlExprMeta {
    private final ISqlExprMeta idExprMeta;
    private final IEntityComponentModel componentModel;
    /**
     * columnBinders必须按照id+component字段的顺序进行排列
     */
    private final List<IDataParameterBinder> columnBinders;
    private final List<String> columnNames;
    private final IntArray propIds;

    public ComponentExprMeta(IDialect dialect, ISqlExprMeta idExprMeta, IEntityComponentModel componentModel,
                             List<IDataParameterBinder> columnBinders) {
        this.idExprMeta = idExprMeta;
        this.componentModel = componentModel;
        this.columnBinders = columnBinders;
        this.columnNames = buildColumnNames(dialect, idExprMeta, componentModel);
        this.propIds = buildPropIds(componentModel);
    }

    List<String> buildColumnNames(IDialect dialect, ISqlExprMeta idProp, IEntityComponentModel compModel) {
        List<String> colNames = new ArrayList<>(idProp.getColumnCount() + compModel.getColumns().size());
        colNames.addAll(idProp.getColumnNames());
        for (IColumnModel col : compModel.getColumns()) {
            if (!col.isPrimary()) {
                colNames.add(EqlHelper.getColumnName(dialect, col));
            }
        }
        return colNames;
    }

    IntArray buildPropIds(IEntityComponentModel compModel) {
        MutableIntArray propIds = new MutableIntArray(compModel.getColumns().size());
        for (IColumnModel col : compModel.getColumns()) {
            if (!col.isPrimary()) {
                propIds.add(col.getPropId());
            }
        }
        return propIds.toImmutable();
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
        return componentModel;
    }

    @Override
    public Object buildValue(Object[] row, int fromIndex, IEqlQueryContext session) {
        // 这里假设了总是包含实体主键字段，且主键字段排在最前面
        Object id = idExprMeta.buildValue(row, fromIndex, session);
        if (id == null)
            return null;

        Object[] values = new Object[propIds.size()];
        // 跳过主键部分
        System.arraycopy(row, fromIndex + idExprMeta.getColumnCount(), values, 0, values.length);

//        IOrmEntity entity = session.internalLoad(componentModel.getOwnerEntityModel().getName(), id);
//        session.internalAssemble(entity, values, propIds);
        IDaoEntity entity = session.internalMakeEntity(componentModel.getOwnerEntityModel().getName(),id, values, propIds);

        return entity.orm_propValueByName(componentModel.getName());
    }
}