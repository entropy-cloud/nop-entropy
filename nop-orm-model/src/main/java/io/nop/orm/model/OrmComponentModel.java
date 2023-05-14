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
import io.nop.orm.model._gen._OrmComponentModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.model.OrmModelErrors.ARG_COMPONENT_CLASS;
import static io.nop.orm.model.OrmModelErrors.ARG_PROP_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_UNKNOWN_COMPONENT_PROP;

public class OrmComponentModel extends _OrmComponentModel implements IEntityComponentModel {
    private OrmEntityModel ownerEntityModel;

    private Map<String, Integer> colPropIds;

    public OrmComponentModel() {

    }

    @Override
    public OrmEntityModel getOwnerEntityModel() {
        return ownerEntityModel;
    }

    public void setOwnerEntityModel(OrmEntityModel ownerEntityModel) {
        this.ownerEntityModel = ownerEntityModel;
    }

    @Override
    public Map<String, Integer> getColumnPropIdMap() {
        if (colPropIds == null) {
            colPropIds = new HashMap<>();
            for (OrmComponentPropModel propModel : this.getProps()) {
                colPropIds.put(propModel.getName(), propModel.getColumnPropId());
            }
        }
        return colPropIds;
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

    @Override
    public IEntityComponentPropModel requireProp(String name) {
        IEntityComponentPropModel prop = getProp(name);
        if (prop == null)
            throw new NopException(ERR_ORM_MODEL_UNKNOWN_COMPONENT_PROP).source(this)
                    .param(ARG_COMPONENT_CLASS, getClassName()).param(ARG_PROP_NAME, name);
        return prop;
    }

    @Override
    public List<? extends IColumnModel> getColumns() {
        List<OrmColumnModel> cols = new ArrayList<>(getProps().size());
        for (OrmComponentPropModel prop : getProps()) {
            cols.add(prop.getColumnModel());
        }
        return cols;
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
        for (OrmComponentPropModel prop : getProps()) {
            if (prop.getColumnModel().isLazy())
                return true;
        }
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
        return OrmDataTypeKind.COMPONENT;
    }

    @Override
    public String getComment() {
        return null;
    }

}
