package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.commons.util.StringHelper;
import io.nop.dyn.dao.entity._gen._NopDynEntityMeta;


@BizObjName("NopDynEntityMeta")
public class NopDynEntityMeta extends _NopDynEntityMeta {

    public String forceGetTableName() {
        if (!StringHelper.isEmpty(getTableName()))
            return getTableName();
        String simpleName = StringHelper.simpleClassName(getEntityName());
        return StringHelper.camelCaseToUnderscore(simpleName, true);
    }

    public String getBizObjName() {
        return StringHelper.simplifyJavaType(getEntityName());
    }
}