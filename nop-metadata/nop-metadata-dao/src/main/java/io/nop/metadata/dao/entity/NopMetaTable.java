package io.nop.metadata.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.metadata.dao.NopMetadataDaoConstants;
import io.nop.metadata.dao.entity._gen._NopMetaTable;


@BizObjName("NopMetaTable")
public class NopMetaTable extends _NopMetaTable{

    /** 稳定领域判定（审计 MD-2 下沉）：tableType 三分派的实体侧唯一真相源。 */
    public boolean isEntityTable() {
        return NopMetadataDaoConstants.TABLE_TYPE_ENTITY.equals(getTableType());
    }

    public boolean isExternalTable() {
        return NopMetadataDaoConstants.TABLE_TYPE_EXTERNAL.equals(getTableType());
    }

    public boolean isSqlTable() {
        return NopMetadataDaoConstants.TABLE_TYPE_SQL.equals(getTableType());
    }
}
