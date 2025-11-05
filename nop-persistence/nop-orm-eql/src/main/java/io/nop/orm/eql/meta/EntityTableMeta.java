/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.meta;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.IntArray;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.objects.PropPath;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.eql.binder.IOrmColumnBinderEnhancer;
import io.nop.orm.eql.binder.OrmBinderHelper;
import io.nop.orm.eql.utils.EqlHelper;
import io.nop.orm.model.ExprOrmDataType;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityComponentModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmEntityFilterModel;
import io.nop.orm.model.OrmModelConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.eql.OrmEqlErrors.ARG_ENTITY_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ARG_PROP_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FIELD_IN_ENTITY;
import static io.nop.orm.eql.utils.EqlHelper.getColumnName;

public class EntityTableMeta implements ISqlTableMeta {
    private final EntityExprMeta entityExprMeta;
    private final Map<String, ISqlExprMeta> propExprMetas = new LinkedHashMap<>();

    private final Map<String, ISqlExprMeta> valueExprMetas;

    private final List<OrmEntityFilterModel> filters;

    public EntityTableMeta(IEntityModel entityModel, IOrmColumnBinderEnhancer enhancer, IDialect dialect) {
        IDataParameterBinder[] colBinders = OrmBinderHelper.buildBinders(dialect, entityModel, enhancer);
        IntArray propIds = entityModel.getEagerLoadProps();
        List<IDataParameterBinder> binders = new ArrayList<>(propIds.size());
        for (int propId : propIds) {
            binders.add(colBinders[propId]);
        }
        entityExprMeta = new EntityExprMeta(dialect, entityModel, propIds, binders);
        IEntityPropModel idProp = entityModel.getIdProp();
        ISqlExprMeta idExprMeta = null;
        if (idProp != null) {
            if (idProp.isSingleColumn()) {
                idExprMeta = makeColumnMeta(dialect, idProp, colBinders);
            } else {
                idExprMeta = makeCompositePkMeta(dialect, idProp, colBinders);
            }
            this.propExprMetas.put(OrmModelConstants.PROP_ID, idExprMeta);
        }

        for (IColumnModel colModel : entityModel.getColumns()) {
            propExprMetas.put(colModel.getName(), makeColumnMeta(dialect, colModel, colBinders));
        }

        // 无主键的实体不支持查询component，因为component需要知道owner对象， 无主键的情况下无法通过简单的方式获取到owner实体
        if (entityModel.isNoPrimaryKey()) {
            for (IEntityComponentModel propModel : entityModel.getComponents()) {
                propExprMetas.put(propModel.getName(), makeComponentMeta(dialect, idExprMeta, propModel, colBinders));
            }
        }

        for (IEntityRelationModel propModel : entityModel.getRelations()) {
            ISqlExprMeta relMeta = makePropMeta(dialect, idExprMeta, propModel, colBinders);
            if (relMeta == null)
                continue;

            propExprMetas.put(propModel.getName(), relMeta);
        }

        if (entityModel.isKvTable()) {
            valueExprMetas = new HashMap<>();
            addKvTableFields(dialect);
        } else {
            valueExprMetas = Collections.emptyMap();
        }

        this.filters = entityModel.getFilters();
    }

    @Override
    public String getEntityName() {
        return this.entityExprMeta.getEntityName();
    }

    public boolean isUseLogicalDelete() {
        return entityExprMeta.getEntityModel().isUseLogicalDelete();
    }

//    public ISqlExprMeta getDeleteFlagPropMeta() {
//        return propExprMetas.get(getEntityModel().getDeleteFlagProp());
//    }

    @Override
    public ISqlExprMeta requireFieldExprMeta(String name, boolean allowUnderscoreName) {
        ISqlExprMeta exprMeta = getFieldExprMeta(name, allowUnderscoreName);
        if (exprMeta == null)
            throw new NopException(ERR_EQL_UNKNOWN_FIELD_IN_ENTITY)
                    .param(ARG_ENTITY_NAME, getEntityName())
                    .param(ARG_PROP_NAME, name);
        return exprMeta;
    }

    @Override
    public List<OrmEntityFilterModel> getFilters() {
        return filters;
    }

    public String getDeleteFlagPropName() {
        return getEntityModel().getDeleteFlagProp();
    }

    public Object getDeleteFlagValue(boolean b, IDialect dialect) {
        IEntityModel entityModel = getEntityModel();
        IColumnModel delFlagCol = entityModel.getColumnByPropId(entityModel.getDeleteFlagPropId(), false);
        return EqlHelper.getBooleanValue(delFlagCol.getStdSqlType(), dialect, false);
    }

    private void addKvTableFields(IDialect dialect) {
        ISqlExprMeta stringValue = propExprMetas.get(OrmModelConstants.PROP_NAME_stringValue);
        ISqlExprMeta decimalValue = propExprMetas.get(OrmModelConstants.PROP_NAME_decimalValue);
        ISqlExprMeta intValue = propExprMetas.get(OrmModelConstants.PROP_NAME_intValue);
        ISqlExprMeta booleanValue = propExprMetas.get(OrmModelConstants.PROP_NAME_booleanValue);
        ISqlExprMeta charValue = propExprMetas.get(OrmModelConstants.PROP_NAME_charValue);
        ISqlExprMeta byteValue = propExprMetas.get(OrmModelConstants.PROP_NAME_byteValue);
        ISqlExprMeta shortValue = propExprMetas.get(OrmModelConstants.PROP_NAME_shortValue);
        ISqlExprMeta longValue = propExprMetas.get(OrmModelConstants.PROP_NAME_longValue);
        ISqlExprMeta floatValue = propExprMetas.get(OrmModelConstants.PROP_NAME_floatValue);
        ISqlExprMeta doubleValue = propExprMetas.get(OrmModelConstants.PROP_NAME_doubleValue);
        ISqlExprMeta dateValue = propExprMetas.get(OrmModelConstants.PROP_NAME_dateValue);
        ISqlExprMeta dateTimeValue = propExprMetas.get(OrmModelConstants.PROP_NAME_dateTimeValue);
        ISqlExprMeta timestampValue = propExprMetas.get(OrmModelConstants.PROP_NAME_timestampValue);

        if (stringValue != null) {
            valueExprMetas.put(StdDataType.STRING.getName(), stringValue);
        }

        if (decimalValue != null) {
            valueExprMetas.put(StdDataType.DECIMAL.getName(), decimalValue);
        }

        if (intValue != null) {
            valueExprMetas.put(StdDataType.INT.getName(), intValue);
        } else if (decimalValue != null) {
            valueExprMetas.put(StdDataType.INT.getName(),
                    makeValueType(decimalValue, dialect, StdSqlType.DECIMAL, StdDataType.INT));
        }

        if (booleanValue != null) {
            valueExprMetas.put(StdDataType.BOOLEAN.getName(), booleanValue);
        } else if (decimalValue != null) {
            valueExprMetas.put(StdDataType.BOOLEAN.getName(),
                    makeValueType(decimalValue, dialect, StdSqlType.DECIMAL, StdDataType.BOOLEAN));
        }

        if (charValue != null) {
            valueExprMetas.put(StdDataType.CHAR.getName(), charValue);
        } else if (stringValue != null) {
            valueExprMetas.put(StdDataType.CHAR.getName(),
                    makeValueType(stringValue, dialect, StdSqlType.VARCHAR, StdDataType.CHAR));
        }

        if (byteValue != null) {
            valueExprMetas.put(StdDataType.BYTE.getName(), byteValue);
        } else if (decimalValue != null) {
            valueExprMetas.put(StdDataType.BYTE.getName(),
                    makeValueType(decimalValue, dialect, StdSqlType.DECIMAL, StdDataType.BYTE));
        }

        if (shortValue != null) {
            valueExprMetas.put(StdDataType.SHORT.getName(), shortValue);
        } else if (decimalValue != null) {
            valueExprMetas.put(StdDataType.SHORT.getName(),
                    makeValueType(decimalValue, dialect, StdSqlType.DECIMAL, StdDataType.SHORT));
        }

        if (longValue != null) {
            valueExprMetas.put(StdDataType.LONG.getName(), longValue);
        } else if (decimalValue != null) {
            valueExprMetas.put(StdDataType.LONG.getName(),
                    makeValueType(decimalValue, dialect, StdSqlType.DECIMAL, StdDataType.LONG));
        }

        if (floatValue != null) {
            valueExprMetas.put(StdDataType.FLOAT.getName(), floatValue);
        } else if (decimalValue != null) {
            valueExprMetas.put(StdDataType.FLOAT.getName(),
                    makeValueType(decimalValue, dialect, StdSqlType.DECIMAL, StdDataType.FLOAT));
        }

        if (doubleValue != null) {
            valueExprMetas.put(StdDataType.DOUBLE.getName(), doubleValue);
        } else if (decimalValue != null) {
            valueExprMetas.put(StdDataType.DOUBLE.getName(),
                    makeValueType(decimalValue, dialect, StdSqlType.DECIMAL, StdDataType.DOUBLE));
        }

        if (dateValue != null) {
            valueExprMetas.put(StdDataType.DATE.getName(), dateValue);
        }

        if (dateTimeValue != null) {
            valueExprMetas.put(StdDataType.DATETIME.getName(), dateTimeValue);
        } else if (timestampValue != null) {
            valueExprMetas.put(StdDataType.DATETIME.getName(),
                    makeValueType(decimalValue, dialect, StdSqlType.DECIMAL, StdDataType.TIMESTAMP));
        }

        if (timestampValue != null) {
            valueExprMetas.put(StdDataType.TIMESTAMP.getName(), timestampValue);
        }
    }

    private ISqlExprMeta makeValueType(ISqlExprMeta baseMeta, IDialect dialect, StdSqlType sqlType,
                                       StdDataType dataType) {
        IDataParameterBinder binder = dialect.getDataParameterBinder(dataType, sqlType);
        if (binder == null) {
            return baseMeta;
        }
        return new SingleColumnExprMeta(baseMeta.getColumnNames().get(0), binder, ExprOrmDataType.fromSqlType(sqlType));
    }

    public ISqlExprMeta getValueExprMeta(String name) {
        return valueExprMetas.get(name);
    }

    public boolean isKvTable() {
        return getEntityModel().isKvTable();
    }

    public IEntityModel getEntityModel() {
        return entityExprMeta.getEntityModel();
    }

    public String getName() {
        return entityExprMeta.getEntityModel().getName();
    }

    public String getQuerySpace() {
        return entityExprMeta.getEntityModel().getQuerySpace();
    }

    @Override
    public PropPath getAliasPropPath(String name) {
        return entityExprMeta.getEntityModel().getAliasPropPath(name);
    }

    public ISqlExprMeta getEntityExprMeta() {
        return entityExprMeta;
    }

    public Map<String, ISqlExprMeta> getFieldExprMetas() {
        return propExprMetas;
    }

    public ISqlExprMeta getFieldExprMeta(String propName, boolean allowUnderscoreName) {
        ISqlExprMeta exprMeta = propExprMetas.get(propName);
        if (exprMeta == null)
            exprMeta = valueExprMetas.get(propName);

        if (exprMeta == null && allowUnderscoreName) {
            IEntityModel entityModel = getEntityModel();
            IEntityPropModel propModel = entityModel.getPropByUnderscoreName(propName);
            if (propModel != null) {
                exprMeta = propExprMetas.get(propModel.getName());
            }
        }
        return exprMeta;
    }

    SingleColumnExprMeta makeColumnMeta(IDialect dialect, IEntityPropModel propModel, IDataParameterBinder[] binders) {
        IDataParameterBinder binder = binders[propModel.getColumnPropId()];
        String colName = getColumnName(dialect, (IColumnModel) propModel);
        return new SingleColumnExprMeta(colName, binder, propModel);
    }

    CompositePkExprMeta makeCompositePkMeta(IDialect dialect, IEntityPropModel idProp, IDataParameterBinder[] binders) {
        List<IDataParameterBinder> idBinders = new ArrayList<>(idProp.getColumns().size());
        for (int propId : idProp.getColumnPropIds()) {
            idBinders.add(binders[propId]);
        }
        List<String> colNames = EqlHelper.getPropColumnNames(dialect, idProp);
        return new CompositePkExprMeta(colNames, idBinders, idProp);
    }

    ComponentExprMeta makeComponentMeta(IDialect dialect, ISqlExprMeta idExprMeta, IEntityComponentModel propModel,
                                        IDataParameterBinder[] colBinders) {
        List<IDataParameterBinder> binders = new ArrayList<>();
        if (idExprMeta != null) {
            binders.addAll(idExprMeta.getColumnBinders());
        }
        for (int propId : propModel.getColumnPropIds()) {
            binders.add(colBinders[propId]);
        }
        return new ComponentExprMeta(dialect, idExprMeta, propModel, binders);
    }

    ISqlExprMeta makePropMeta(IDialect dialect, ISqlExprMeta idExprMeta, IEntityRelationModel propModel,
                              IDataParameterBinder[] colBinders) {
        if (idExprMeta == null)
            return makePropMetaNoPk(dialect, propModel, colBinders);
        if (propModel.isToOneRelation()) {
            List<IDataParameterBinder> binders = new ArrayList<>();
            List<String> colNames = new ArrayList<>();
            for (IEntityJoinConditionModel join : propModel.getJoin()) {
                if (join.getLeftPropModel() != null) {
                    if (join.getLeftPropModel().isColumnModel()) {
                        colNames.add(getColumnName(dialect, (IColumnModel) join.getLeftPropModel()));
                        binders.add(colBinders[join.getRightPropModel().getColumnPropId()]);
                    } else {
                        if (idExprMeta == null)
                            return null;
                        // 基于computed或者alias属性进行关联，
                        return new EntityPropExprMeta(idExprMeta, propModel);
                    }
                }
            }
            return new EntityRefPropExprMeta(colNames, binders, propModel);
        } else {
            return new EntityPropExprMeta(idExprMeta, propModel);
        }
    }

    ISqlExprMeta makePropMetaNoPk(IDialect dialect, IEntityRelationModel propModel, IDataParameterBinder[] colBinders) {
        return null;
    }
}