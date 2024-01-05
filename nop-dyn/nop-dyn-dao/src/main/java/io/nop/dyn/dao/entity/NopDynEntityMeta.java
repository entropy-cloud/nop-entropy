package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.commons.util.StringHelper;
import io.nop.dyn.dao.entity._gen._NopDynEntityMeta;
import io.nop.orm.model.IEntityModel;


@BizObjName("NopDynEntityMeta")
public class NopDynEntityMeta extends _NopDynEntityMeta {

    private IEntityModel entityModel;

    public IEntityModel getEntityModel() {
        return entityModel;
    }

    public void setEntityModel(IEntityModel entityModel) {
        this.entityModel = entityModel;
    }

    /**
     * 是否定义实体属性。如果没有定义实体属性，则不需要生成实体对象和xmeta，仅生成xbiz
     */
    public boolean isHasProp() {
        return !getPropMetas().isEmpty();
    }

    public String forceGetTableName() {
        if (!StringHelper.isEmpty(getTableName()))
            return getTableName();
        String simpleName = StringHelper.simpleClassName(getEntityName());
        return StringHelper.camelCaseToUnderscore(simpleName, true);
    }

    public String getBizObjName() {
        return StringHelper.simpleClassName(getEntityName());
    }
}