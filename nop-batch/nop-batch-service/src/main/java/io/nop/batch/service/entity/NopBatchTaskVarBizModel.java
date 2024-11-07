
package io.nop.batch.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.batch.dao.entity.NopBatchTaskVar;

@BizModel("NopBatchTaskVar")
public class NopBatchTaskVarBizModel extends CrudBizModel<NopBatchTaskVar>{
    public NopBatchTaskVarBizModel(){
        setEntityName(NopBatchTaskVar.class.getName());
    }
}
