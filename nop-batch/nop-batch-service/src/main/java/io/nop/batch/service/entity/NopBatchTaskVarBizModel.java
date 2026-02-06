
package io.nop.batch.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.batch.dao.entity.NopBatchTaskVar;
import io.nop.batch.biz.INopBatchTaskVarBiz;

@BizModel("NopBatchTaskVar")
public class NopBatchTaskVarBizModel extends CrudBizModel<NopBatchTaskVar> implements INopBatchTaskVarBiz {
    public NopBatchTaskVarBizModel(){
        setEntityName(NopBatchTaskVar.class.getName());
    }
}
