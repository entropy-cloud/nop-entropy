
package io.nop.task.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.task.dao.entity.NopTaskStepInstance;
import io.nop.task.biz.INopTaskStepInstanceBiz;

@BizModel("NopTaskStepInstance")
public class NopTaskStepInstanceBizModel extends CrudBizModel<NopTaskStepInstance> implements INopTaskStepInstanceBiz {
    public NopTaskStepInstanceBizModel(){
        setEntityName(NopTaskStepInstance.class.getName());
    }
}
