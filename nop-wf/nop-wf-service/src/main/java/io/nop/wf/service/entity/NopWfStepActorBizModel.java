
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.dao.entity.NopWfStepActor;

@BizModel("NopWfStepActor")
public class NopWfStepActorBizModel extends CrudBizModel<NopWfStepActor>{
    public NopWfStepActorBizModel(){
        setEntityName(NopWfStepActor.class.getName());
    }
}
