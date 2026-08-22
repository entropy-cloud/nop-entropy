/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.meta;

import io.nop.dao.api.IDaoEntity;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.eql.IEqlQueryContext;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IOrmDataType;

import java.util.List;

/**
 * 多对一关联属性
 */
public class EntityRefPropExprMeta implements ISqlExprMeta {
    private final List<String> colNames;
    private final List<IDataParameterBinder> colBinders;
    private final IEntityRelationModel propModel;

    public EntityRefPropExprMeta(List<String> colNames, List<IDataParameterBinder> colBinders,
                                 IEntityRelationModel propModel) {
        this.colNames = colNames;
        this.colBinders = colBinders;
        this.propModel = propModel;
    }

    @Override
    public List<String> getColumnNames() {
        return colNames;
    }

    @Override
    public int getColumnCount() {
        return colNames.size();
    }

    @Override
    public List<IDataParameterBinder> getColumnBinders() {
        return colBinders;
    }

    @Override
    public IOrmDataType getOrmDataType() {
        return propModel;
    }

    @Override
    public Object buildValue(Object[] row, int fromIndex, IEqlQueryContext session) {
        Object id;
        if (colBinders.size() == 1) {
            id = row[fromIndex];
        } else {
            // pk槽位按join条件在列表中的位置对齐：列条件按出现顺序对应row中的列，常量条件不占用列槽位
            Object[] pk = new Object[propModel.getJoin().size()];
            int colIdx = 0;
            for (int i = 0; i < propModel.getJoin().size(); i++) {
                IEntityJoinConditionModel join = propModel.getJoin().get(i);
                if (join.getLeftPropModel() != null) {
                    pk[i] = row[fromIndex + colIdx];
                    colIdx++;
                } else {
                    pk[i] = join.getLeftValue();
                }
            }
            id = session.castId(propModel.getRefEntityModel(), pk);
        }
        if (id == null)
            return null;
        IDaoEntity entity = session.internalLoad(propModel.getRefEntityName(), id);
        return entity;
    }
}