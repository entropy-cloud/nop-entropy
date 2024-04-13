
package io.nop.task.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.task.dao.entity.NopTaskInstance;

@BizModel("NopTaskInstance")
public class NopTaskInstanceBizModel extends CrudBizModel<NopTaskInstance>{
    public NopTaskInstanceBizModel(){
        setEntityName(NopTaskInstance.class.getName());
    }
}
