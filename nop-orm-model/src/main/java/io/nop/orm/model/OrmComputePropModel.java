/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.orm.model._gen._OrmComputePropModel;
import io.nop.xlang.api.XLang;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_PROP_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_COMPUTE_PROP_NO_GETTER;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_COMPUTE_PROP_NO_SETTER;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COMPUTE_PROP_ARG;

public class OrmComputePropModel extends _OrmComputePropModel implements IComputePropModel {
    private OrmEntityModel ownerEntityModel;

    public OrmComputePropModel() {

    }

    @Override
    public Object getValue(Object entity) {
        IEvalAction getter = getGetter();
        if (getter == null)
            throw new NopException(ERR_ORM_COMPUTE_PROP_NO_GETTER)
                    .param(ARG_ENTITY_NAME, this.getOwnerEntityModel().getName())
                    .param(ARG_PROP_NAME, getName());

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(OrmModelConstants.VAR_ENTITY, entity);
        return getter.invoke(scope);
    }

    @Override
    public void setValue(Object entity, Object value) {
        IEvalAction setter = getSetter();
        if (setter == null)
            throw new NopException(ERR_ORM_COMPUTE_PROP_NO_SETTER)
                    .param(ARG_ENTITY_NAME, this.getOwnerEntityModel().getName())
                    .param(ARG_PROP_NAME, getName());

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(OrmModelConstants.VAR_ENTITY, entity);
        scope.setLocalValue(OrmModelConstants.VAR_VALUE, value);
        setter.invoke(scope);
    }

    @Override
    public Object computeValue(Object entity, Map<String, Object> args) {
        IEvalAction getter = getGetter();
        if (getter == null)
            throw new NopException(ERR_ORM_COMPUTE_PROP_NO_GETTER)
                    .param(ARG_ENTITY_NAME, this.getOwnerEntityModel().getName())
                    .param(ARG_PROP_NAME, getName());

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(OrmModelConstants.VAR_ENTITY, entity);
        if (args == null)
            args = Collections.emptyMap();

        for (OrmComputeArgModel argModel : getArgs()) {
            Object value = args.get(argModel.getName());
            value = BeanTool.castBeanToType(value, argModel.getType());
            scope.setLocalValue(argModel.getName(), value);
        }

        for (String argName : args.keySet()) {
            if (getArg(argName) == null)
                throw new NopException(ERR_ORM_UNKNOWN_COMPUTE_PROP_ARG)
                        .param(ARG_ENTITY_NAME, this.getOwnerEntityModel().getName())
                        .param(ARG_PROP_NAME, getName());
        }
        return getter.invoke(scope);
    }

    @Override
    public String getJavaTypeName() {
        IGenericType type = getType();
        if (type == null)
            return Object.class.getTypeName();
        return type.getTypeName();
    }

    @Override
    public boolean isMandatory() {
        return false;
    }

    @Override
    public OrmEntityModel getOwnerEntityModel() {
        return ownerEntityModel;
    }

    public void setOwnerEntityModel(OrmEntityModel ownerEntityModel) {
        this.ownerEntityModel = ownerEntityModel;
    }

    @Override
    public List<? extends IColumnModel> getColumns() {
        return null;
    }

    @Override
    public StdDataType getStdDataType() {
        IGenericType type = getType();
        return type == null ? StdDataType.ANY : type.getStdDataType();
    }

    @Override
    public boolean isSingleColumn() {
        return false;
    }

    @Override
    public boolean hasLazyLoadColumn() {
        return false;
    }

    @Override
    public int getColumnPropId() {
        return -1;
    }

    @Override
    public int[] getColumnPropIds() {
        return new int[0];
    }

    @Override
    public String getAliasPropPath() {
        return null;
    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.COMPUTE;
    }

    @Override
    public String getComment() {
        return null;
    }

}
