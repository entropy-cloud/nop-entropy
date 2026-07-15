
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaEntityBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;

@BizModel("NopMetaEntity")
public class NopMetaEntityBizModel extends CrudBizModel<NopMetaEntity> implements INopMetaEntityBiz{
    public NopMetaEntityBizModel(){
        setEntityName(NopMetaEntity.class.getName());
    }
}
