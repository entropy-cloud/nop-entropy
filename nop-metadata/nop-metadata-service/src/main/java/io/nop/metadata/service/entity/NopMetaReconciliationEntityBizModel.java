
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaReconciliationEntityBiz;
import io.nop.metadata.dao.entity.NopMetaReconciliationEntity;

@BizModel("NopMetaReconciliationEntity")
public class NopMetaReconciliationEntityBizModel extends CrudBizModel<NopMetaReconciliationEntity> implements INopMetaReconciliationEntityBiz{
    public NopMetaReconciliationEntityBizModel(){
        setEntityName(NopMetaReconciliationEntity.class.getName());
    }
}
