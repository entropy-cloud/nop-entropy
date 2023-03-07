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
import io.nop.orm.model._gen._OrmAliasModel;

import java.util.List;
import java.util.Set;

public class OrmAliasModel extends _OrmAliasModel implements IEntityAliasModel {
    private OrmEntityModel ownerEntityModel;

    public OrmAliasModel() {

    }

    public String getJavaTypeName() {
        return getType().toString();
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
        IGenericType type = getType();
        return type == null ? StdDataType.ANY : type.getStdDataType();
    }

    @Override
    public boolean isSingleColumn() {
        return false;
    }

    @Override
    public boolean isMandatory() {
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
        return getPropPath();
    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.ALIAS;
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
