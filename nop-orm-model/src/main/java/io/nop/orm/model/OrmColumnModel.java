/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.orm.model._gen._OrmColumnModel;

import java.util.Collections;
import java.util.List;

public class OrmColumnModel extends _OrmColumnModel implements IColumnModel {
    private OrmEntityModel ownerEntityModel;

    private List<IEntityRelationModel> columnRefs;

    private String sqlType;

    public OrmColumnModel() {
        setInsertable(true);
        setUpdatable(true);
    }

    public String toString() {
        return getClass().getSimpleName() + "[name=" + getName() + ",code=" + getCode() + "]@" + getLocation();
    }

    public OrmEntityModel getOwnerEntityModel() {
        return ownerEntityModel;
    }

    public void setOwnerEntityModel(OrmEntityModel ownerEntityModel) {
        this.ownerEntityModel = ownerEntityModel;
    }

//    @Override
//    public Object getPropValue(IOrmEntity entity) {
//        return entity.orm_propValue(getPropId());
//    }
//
//    @Override
//    public void setPropValue(IOrmEntity entity, Object value) {
//        entity.orm_propValue(getPropId(), value);
//    }

    @Override
    public List<? extends IColumnModel> getColumns() {
        return Collections.singletonList(this);
    }

    @Override
    public boolean isSingleColumn() {
        return true;
    }

    @Override
    public boolean hasLazyLoadColumn() {
        return isLazy();
    }

    @Override
    public int getColumnPropId() {
        return getPropId();
    }

    @Override
    public int[] getColumnPropIds() {
        return new int[]{getPropId()};
    }

    @Override
    public String getAliasPropPath() {
        return null;
    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.COLUMN;
    }

    public List<IEntityRelationModel> getColumnRefs() {
        return columnRefs;
    }

    public void setColumnRefs(List<IEntityRelationModel> columnRefs) {
        this.columnRefs = columnRefs;
    }


    public String getSqlType() {
        return sqlType;
    }

    public void setSqlType(String sqlType) {
        this.sqlType = sqlType;
    }
}
