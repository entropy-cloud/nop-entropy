
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaDataSourceBiz;
import io.nop.metadata.dao.entity.NopMetaDataSource;

@BizModel("NopMetaDataSource")
public class NopMetaDataSourceBizModel extends CrudBizModel<NopMetaDataSource> implements INopMetaDataSourceBiz{
    public NopMetaDataSourceBizModel(){
        setEntityName(NopMetaDataSource.class.getName());
    }
}
