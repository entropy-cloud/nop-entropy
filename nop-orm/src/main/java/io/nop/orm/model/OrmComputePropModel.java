/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.commons.type.StdDataType;
import io.nop.core.type.IGenericType;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model._gen._OrmComputePropModel;

import java.util.List;
import java.util.Set;

public class OrmComputePropModel extends _OrmComputePropModel implements IComputePropModel {
    private OrmEntityModel ownerEntityModel;

    public OrmComputePropModel() {

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
    public Object getPropValue(IOrmEntity entity) {
        return entity.orm_propValueByName(getName());
    }

    @Override
    public void setPropValue(IOrmEntity entity, Object value) {
        entity.orm_propValueByName(getName(), value);
    }

    @Override
    public List<? extends IColumnModel> getColumns() {
        return null;
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.ANY;
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
        return 0;
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

    @Override
    public Set<String> getTagSet() {
        return null;
    }
}
