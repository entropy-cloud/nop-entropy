/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.core.lang.sql.binder.IDataParameterBinder;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IOrmDataType;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.support.OrmCompositePk;

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
    public Object buildValue(Object[] row, int fromIndex, IOrmSessionImplementor session) {
        Object id;
        if (colBinders.size() == 1) {
            id = row[fromIndex];
        } else {
            Object[] pk = new Object[propModel.getJoin().size()];
            int idx = 0;
            for (IEntityJoinConditionModel join : propModel.getJoin()) {
                if (join.getLeftPropModel() != null) {
                    pk[idx] = row[fromIndex + idx];
                    idx++;
                } else {
                    pk[idx] = join.getLeftValue();
                }
            }
            id = OrmCompositePk.build(propModel.getRefEntityModel(), pk);
        }
        if (id == null)
            return null;
        IOrmEntity entity = session.internalLoad(propModel.getRefEntityName(), id);
        return entity;
    }
}