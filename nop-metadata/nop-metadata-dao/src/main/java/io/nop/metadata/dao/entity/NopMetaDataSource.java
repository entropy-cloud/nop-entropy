package io.nop.metadata.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.metadata.dao.entity._gen._NopMetaDataSource;


@BizObjName("NopMetaDataSource")
public class NopMetaDataSource extends _NopMetaDataSource{

    /** 稳定领域判定（审计 MD-2 下沉）：数据源停用状态的实体侧唯一真相源。 */
    public boolean isDisabled() {
        return io.nop.metadata.dao.NopMetadataDaoConstants.DATASOURCE_STATUS_DISABLED.equals(getStatus());
    }



}
