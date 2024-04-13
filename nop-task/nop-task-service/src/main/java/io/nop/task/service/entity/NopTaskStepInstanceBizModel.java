
package io.nop.task.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.task.dao.entity.NopTaskStepInstance;

@BizModel("NopTaskStepInstance")
public class NopTaskStepInstanceBizModel extends CrudBizModel<NopTaskStepInstance>{
    public NopTaskStepInstanceBizModel(){
        setEntityName(NopTaskStepInstance.class.getName());
    }
}
