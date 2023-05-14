/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.param;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoComponent;
import io.nop.dao.api.IDaoEntity;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.model.IEntityComponentModel;
import io.nop.orm.model.IEntityComponentPropModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;

import java.util.List;

import static io.nop.orm.eql.OrmEqlErrors.ARG_EXPECTED;
import static io.nop.orm.eql.OrmEqlErrors.ARG_PARAM_INDEX;
import static io.nop.orm.eql.OrmEqlErrors.ARG_VALUE;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_PARAM_NOT_COMPONENT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_PARAM_NOT_EXPECTED_ENTITY;

public class EntityPropParamBuilder implements ISqlParamBuilder {
    private final int paramIndex;
    private final ISqlExprMeta exprMeta;

    public EntityPropParamBuilder(int paramIndex, ISqlExprMeta exprMeta) {
        this.paramIndex = paramIndex;
        this.exprMeta = exprMeta;
    }

    @Override
    public void buildParams(List<Object> input, List<Object> params) {
        Object value = input.get(paramIndex);
        IEntityPropModel propModel = (IEntityPropModel) exprMeta.getOrmDataType();
        if (propModel.isToOneRelation()) {
            IEntityRelationModel rel = (IEntityRelationModel) propModel;
            if (value == null) {
                for (IEntityJoinConditionModel join : rel.getJoin()) {
                    if (join.getRightPropModel() != null) {
                        params.add(null);
                    } else {
                        params.add(join.getRightValue());
                    }
                }
            } else {
                if (!(value instanceof IDaoEntity))
                    throw new NopException(ERR_EQL_PARAM_NOT_EXPECTED_ENTITY).param(ARG_PARAM_INDEX, paramIndex)
                            .param(ARG_VALUE, value).param(ARG_EXPECTED, rel.getRefEntityName());

                IDaoEntity entity = (IDaoEntity) value;
                if (!entity.orm_entityName().equals(rel.getRefEntityName()))
                    throw new NopException(ERR_EQL_PARAM_NOT_EXPECTED_ENTITY).param(ARG_PARAM_INDEX, paramIndex)
                            .param(ARG_VALUE, value).param(ARG_EXPECTED, rel.getRefEntityName());

                for (IEntityJoinConditionModel join : rel.getJoin()) {
                    params.add(getRightValue(join, entity));
                }
            }
        } else if (propModel.isComponentModel()) {
            IEntityComponentModel compModel = (IEntityComponentModel) propModel;
            if (value == null) {
                for (int i = 0; i < compModel.getProps().size(); i++) {
                    params.add(null);
                }
            } else {
                if (!(value instanceof IDaoComponent))
                    throw new NopException(ERR_EQL_PARAM_NOT_COMPONENT).param(ARG_PARAM_INDEX, paramIndex)
                            .param(ARG_VALUE, value).param(ARG_EXPECTED, compModel.getClassName());

                IDaoComponent comp = (IDaoComponent) value;
                for (IEntityComponentPropModel compProp : compModel.getProps()) {
                    Object compValue = comp.orm_propValueByName(compProp.getName());
                    params.add(compValue);
                }
            }
        } else if (propModel.isColumnModel()) {
            params.add(value);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public static Object getRightValue(IEntityJoinConditionModel join, IDaoEntity entity) {
        IEntityPropModel propModel = join.getRightPropModel();
        if (propModel != null)
            return entity.orm_propValueByName(propModel.getName());
        return join.getRightValue();
    }
}