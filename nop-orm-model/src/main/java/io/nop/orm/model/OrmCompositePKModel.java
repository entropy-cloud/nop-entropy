/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.type.StdDataType;
import io.nop.orm.model.utils.OrmModelHelper;

import java.util.List;
import java.util.Set;

public class OrmCompositePKModel implements IEntityPropModel {
    private final IEntityModel entityModel;
    private final int[] propIds;

    public OrmCompositePKModel(IEntityModel entityModel, List<OrmColumnModel> columns) {
        this.entityModel = entityModel;
        this.propIds = OrmModelHelper.getPropIds(columns);
    }

    @Override
    public boolean isCompositePk() {
        return true;
    }

    @Override
    public String getJavaTypeName() {
        return OrmModelConstants.COMPOSITE_PK_CLASS_NAME;
    }

    @Override
    public String getDisplayName() {
        return OrmModelConstants.PROP_ID;
    }

    @Override
    public boolean isMandatory() {
        return true;
    }

    @Override
    public SourceLocation getLocation() {
        return entityModel.getLocation();
    }

    @Override
    public IEntityModel getOwnerEntityModel() {
        return entityModel;
    }

//    @Override
//    public Object getPropValue(IOrmEntity entity) {
//        return entity.orm_idString();
//    }
//
//    @Override
//    public void setPropValue(IOrmEntity entity, Object value) {
//        Object id = OrmEntityHelper.castId(entityModel, value);
//        OrmEntityHelper.setId(entityModel, entity, id);
//    }

    @Override
    public List<? extends IColumnModel> getColumns() {
        return entityModel.getPkColumns();
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.STRING;
    }

    @Override
    public String getName() {
        return OrmModelConstants.PROP_ID;
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
        return propIds;
    }

    @Override
    public String getAliasPropPath() {
        return null;
    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.ID;
    }

    @Override
    public String getComment() {
        return null;
    }

    @Override
    public Set<String> getTagSet() {
        return null;
    }

    @Override
    public Object prop_get(String propName) {
        return null;
    }

    @Override
    public boolean prop_has(String propName) {
        return false;
    }
}
