
package io.nop.task.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.task.dao.entity.NopTaskInstance;
import io.nop.task.biz.INopTaskInstanceBiz;

@BizModel("NopTaskInstance")
public class NopTaskInstanceBizModel extends CrudBizModel<NopTaskInstance> implements INopTaskInstanceBiz {
    public NopTaskInstanceBizModel(){
        setEntityName(NopTaskInstance.class.getName());
    }
}
