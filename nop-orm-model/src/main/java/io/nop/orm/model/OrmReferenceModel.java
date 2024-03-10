/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.commons.type.StdDataType;
import io.nop.orm.model._gen._OrmReferenceModel;

import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.model.OrmModelConstants.ENTITY_SET_CLASS_NAME;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(name = "to-one", value = OrmToOneReferenceModel.class),
        @JsonSubTypes.Type(name = "to-many", value = OrmToManyReferenceModel.class),})
public abstract class OrmReferenceModel extends _OrmReferenceModel implements IEntityRelationModel {
    private OrmEntityModel ownerEntityModel;
    private OrmEntityModel refEntityModel;
    private OrmColumnModel column;
    private OrmJoinOnModel singleColumnJoin;

    /**
     * 所有字段都是非空的
     */
    private boolean mandatory;

    private List<OrmColumnModel> columns;
    private int[] propIds;
    private int[] refPropIds;

    private boolean dynamicRelation;

    public OrmReferenceModel() {
        setQueryable(true);
    }

    @Override
    public boolean isDynamicRelation() {
        return dynamicRelation;
    }

    public void setDynamicRelation(boolean dynamicRelation) {
        this.dynamicRelation = dynamicRelation;
    }

    public String getType() {
        return isToOneRelation() ? "to-one" : "to-many";
    }

//    @Override
//    public Object getPropValue(IOrmEntity entity) {
//        return entity.orm_propValueByName(getName());
//    }
//
//    @Override
//    public void setPropValue(IOrmEntity entity, Object value) {
//        entity.orm_propValueByName(getName(), value);
//    }

    public void addJoinOn(OrmJoinOnModel joinOn) {
        List<OrmJoinOnModel> joins = getJoin();
        if (joins == null || joins.isEmpty()) {
            joins = new ArrayList<>(2);
        }
        joins.add(joinOn);
        setJoin(joins);
    }

    @Override
    public List<OrmColumnModel> getColumns() {
        return columns;
    }

    public void setColumns(List<OrmColumnModel> columns) {
        checkAllowChange();
        this.columns = columns;
        if (columns.size() == 1) {
            this.column = columns.get(0);
            this.propIds = new int[]{column.getPropId()};
            this.mandatory = column.isMandatory();
        } else {
            propIds = new int[columns.size()];
            mandatory = true;
            for (int i = 0, n = columns.size(); i < n; i++) {
                propIds[i] = columns.get(i).getPropId();
                if (!columns.get(i).isMandatory()) {
                    mandatory = false;
                }
            }
        }
    }

    @Override
    public StdDataType getStdDataType() {
        return StdDataType.ANY;
    }

    @Override
    public boolean isSingleColumn() {
        return column != null;
    }

    @Override
    public boolean hasLazyLoadColumn() {
        if (column != null)
            return column.isLazy();
        for (OrmColumnModel col : columns) {
            if (col.isLazy())
                return true;
        }
        return false;
    }

    @Override
    public int getColumnPropId() {
        return column == null ? -1 : column.getPropId();
    }

    @Override
    public int[] getRefPropIds() {
        return refPropIds;
    }

    public void setRefPropIds(int[] refPropIds) {
        this.refPropIds = refPropIds;
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
    public OrmEntityModel getOwnerEntityModel() {
        return ownerEntityModel;
    }

    @Override
    public OrmEntityModel getRefEntityModel() {
        return refEntityModel;
    }

    public void setOwnerEntityModel(OrmEntityModel ownerEntityModel) {
        checkAllowChange();
        this.ownerEntityModel = ownerEntityModel;
    }

    public void setRefEntityModel(OrmEntityModel refEntityModel) {
        checkAllowChange();
        this.refEntityModel = refEntityModel;
    }

    @Override
    public OrmJoinOnModel getSingleColumnJoin() {
        return singleColumnJoin;
    }

    public void setSingleColumnJoin(OrmJoinOnModel singleColumnJoin) {
        this.singleColumnJoin = singleColumnJoin;
    }

    @Override
    public List<OrderFieldBean> getSort() {
        return null;
    }

    @Override
    public String getCollectionName() {
        return null;
    }

    @Override
    public String getKeyProp() {
        return null;
    }

    @Override
    public boolean isOneToOne() {
        return false;
    }

    @Override
    public String getJavaTypeName() {
        if (isToOneRelation())
            return getRefEntityName();
        return ENTITY_SET_CLASS_NAME + "<" + getRefEntityName() + ">";
    }

    @Override
    public boolean isMandatory() {
        return mandatory;
    }

    @Override
    public boolean isReverseDepends() {
        return false;
    }
}
